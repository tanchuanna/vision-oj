package com.spingbootinit.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.config.ImageGenerationProperties;
import com.spingbootinit.model.dto.vision.ImageGenerateRequest;
import com.spingbootinit.model.vo.vision.ImageGenerationVO;
import com.spingbootinit.service.AiModelSettingsService;
import com.spingbootinit.service.ImageModelSettingsService;
import com.spingbootinit.service.ImageProviderCatalog;
import com.spingbootinit.utils.MinioUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class ImageGenerationService {
    private static final String DEFAULT_NEGATIVE = "text, watermark, logo, blurry, deformed, duplicate objects, cropped";
    private final ImageGenerationProperties properties;
    private final ObjectMapper objectMapper;
    private final MinioUtils minioUtils;
    private final AiModelSettingsService modelSettings;
    private final ImageModelSettingsService imageSettings;
    private static final Set<String> CLOUD_MODELS = Set.of("qwen-image-2.1-turbo", "qwen-image-2.1-pro");
    private final HttpClient httpClient;

    @Autowired
    public ImageGenerationService(ImageGenerationProperties properties, ObjectMapper objectMapper,
                                  MinioUtils minioUtils, AiModelSettingsService modelSettings,
                                  ImageModelSettingsService imageSettings) {
        this(properties, objectMapper, minioUtils, modelSettings, imageSettings,
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .connectTimeout(Duration.ofSeconds(5)).build());
    }

    ImageGenerationService(ImageGenerationProperties properties, ObjectMapper objectMapper,
                           MinioUtils minioUtils, AiModelSettingsService modelSettings, HttpClient httpClient) {
        this(properties, objectMapper, minioUtils, modelSettings, null, httpClient);
    }

    ImageGenerationService(ImageGenerationProperties properties, ObjectMapper objectMapper,
                           MinioUtils minioUtils, AiModelSettingsService modelSettings,
                           ImageModelSettingsService imageSettings, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.minioUtils = minioUtils;
        this.modelSettings = modelSettings;
        this.imageSettings = imageSettings;
        this.httpClient = httpClient;
    }

    public Map<String, Object> catalog() {
        return Map.of("local", localModels(), "cloud", List.of("qwen-image-2.1-turbo", "qwen-image-2.1-pro"));
    }

    public Map<String, Object> catalog(long userId) {
        var configured = imageSettings.list(userId).stream().map(ImageModelSettingsService.SavedImageConfig::providerCode).toList();
        var providers = ImageProviderCatalog.providers().stream().map(provider -> Map.of(
                "code", provider.code(), "name", provider.name(), "shortName", provider.shortName(),
                "description", provider.description(), "models", provider.models(), "configured", configured.contains(provider.code()))).toList();
        return Map.of("local", localModels(), "cloud", List.of("qwen-image-2.1-turbo", "qwen-image-2.1-pro"), "providers", providers);
    }

    private List<String> localModels() {
        if (!properties.isEnabled()) return List.of();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(properties.getBaseUrl()) + "/object_info/CheckpointLoaderSimple"))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            JsonNode choices = objectMapper.readTree(httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body())
                    .path("CheckpointLoaderSimple").path("input").path("required").path("ckpt_name").path(0);
            List<String> allowed = properties.getAllowedCheckpoints();
            if (!choices.isArray() || allowed == null) return List.of();
            java.util.ArrayList<String> found = new java.util.ArrayList<>();
            for (JsonNode name : choices) if (allowed.contains(name.asText())) found.add(name.asText());
            return found;
        } catch (Exception e) {
            return List.of();
        }
    }

    public ImageGenerationVO generate(ImageGenerateRequest request, long userId) {
        String source = request.getSource() == null || request.getSource().isBlank() ? "LOCAL_COMFYUI" : request.getSource();
        if ("DASHSCOPE".equals(source) && CLOUD_MODELS.contains(request.getModelId())) return generateCloud(request, userId);
        if ("DASHSCOPE".equals(source) || "VOLCENGINE".equals(source) || "BAIDU_QIANFAN".equals(source) || "SILICONFLOW".equals(source)) {
            return generateProviderCloud(request, userId, source);
        }
        if (!"LOCAL_COMFYUI".equals(source)) throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "不支持的生图来源");
        if (!properties.isEnabled()) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "本地 AI 图像生成服务未启用");
        }
        String baseUrl = trimSlash(properties.getBaseUrl());
        String checkpoint = request.getModelId() == null || request.getModelId().isBlank() ? properties.getCheckpoint() : request.getModelId();
        if (!localModels().contains(checkpoint)) throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "本地模型未安装或未获允许，请刷新模型列表");
        long seed = request.getSeed() == null || request.getSeed() < 0
                ? ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE) : request.getSeed();
        long started = System.currentTimeMillis();
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("client_id", UUID.randomUUID().toString());
            body.set("prompt", workflow(request, seed, checkpoint));
            HttpResponse<String> queued = sendJson(baseUrl + "/prompt", body, 20);
            ensureSuccess(queued, "ComfyUI 拒绝了生成任务");
            String promptId = objectMapper.readTree(queued.body()).path("prompt_id").asText();
            if (promptId.isBlank()) throw new IllegalStateException("ComfyUI 未返回 prompt_id");

            JsonNode image = waitForImage(baseUrl, promptId);
            String filename = image.path("filename").asText();
            String subfolder = image.path("subfolder").asText();
            String type = image.path("type").asText("output");
            String query = "filename=" + encode(filename) + "&subfolder=" + encode(subfolder) + "&type=" + encode(type);
            HttpRequest imageRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/view?" + query))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<byte[]> downloaded = httpClient.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (downloaded.statusCode() / 100 != 2 || downloaded.body().length == 0) {
                throw new IllegalStateException("ComfyUI 图片下载失败 HTTP " + downloaded.statusCode());
            }
            String url = minioUtils.uploadBytes(downloaded.body(), "image/png", ".png", "question-images/ai/");
            return ImageGenerationVO.builder().imageUrl(url).promptId(promptId).seed(seed)
                    .generationMs(System.currentTimeMillis() - started).source(source).modelId(checkpoint).build();
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "AI 图像生成已中断");
        } catch (Exception e) {
            log.warn("ComfyUI 图像生成失败", e);
            String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(),
                    "AI 图像生成失败：" + reason + "。请确认 ComfyUI 已在 8188 端口启动且配置的模型文件存在");
        }
    }

    private ImageGenerationVO generateCloud(ImageGenerateRequest request, long userId) {
        String model = request.getModelId();
        if (!CLOUD_MODELS.contains(model)) throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "不支持的百炼生图模型");
        AiModelSettingsService.ModelRoute route = modelSettings.resolve(userId, "DASHSCOPE", null);
        long started = System.currentTimeMillis();
        try {
            URI endpoint = cloudEndpoint();
            boolean nativeApi = endpoint.getPath().startsWith("/api/v1/services/aigc/");
            ObjectNode body = cloudRequestBody(model, request.getPrompt().trim(), nativeApi);
            HttpRequest call = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + route.apiKey()).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> result = httpClient.send(call, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (result.statusCode() == 401 || result.statusCode() == 403)
                throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼生图密钥无效或无该图像模型权限");
            if (result.statusCode() == 402 || result.statusCode() == 429)
                throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼生图额度不足或请求过于频繁");
            if (result.statusCode() == 400)
                throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼拒绝生图请求，请确认当前账号已开通所选图像模型");
            if (result.statusCode() == 404)
                throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼生图接口不存在（HTTP 404），请检查 DASHSCOPE_IMAGE_BASE_URL 是否仍指向旧兼容地址");
            if (result.statusCode() / 100 != 2)
                throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼生图失败（HTTP " + result.statusCode() + "）");
            String url = cloudImageUrl(objectMapper.readTree(result.body()), nativeApi);
            URI imageUri = URI.create(url);
            if (!"https".equalsIgnoreCase(imageUri.getScheme()) || imageUri.getHost() == null
                    || !(imageUri.getHost().equals("aliyuncs.com") || imageUri.getHost().endsWith(".aliyuncs.com")))
                throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼返回的图片地址无效");
            HttpResponse<byte[]> image = httpClient.send(HttpRequest.newBuilder(imageUri).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (image.statusCode() / 100 != 2 || image.body().length == 0 || image.body().length > 10 * 1024 * 1024
                    || image.headers().firstValue("Content-Type").filter(value -> value.toLowerCase().startsWith("image/")).isEmpty())
                throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼图片下载失败或超过 10MB");
            String stored = minioUtils.uploadBytes(image.body(), "image/png", ".png", "question-images/ai/");
            return ImageGenerationVO.builder().imageUrl(stored).source("DASHSCOPE").modelId(model)
                    .generationMs(System.currentTimeMillis() - started).build();
        } catch (BusinessException e) { throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼生图超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼生图已中断，请重试");
        } catch (Exception e) {
            log.warn("百炼生图失败 errorType={}", e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼生图失败，请检查网络或稍后重试");
        }
    }

    private ImageGenerationVO generateProviderCloud(ImageGenerateRequest request, long userId, String providerCode) {
        ImageProviderCatalog.ImageModel model = ImageProviderCatalog.requireModel(providerCode, request.getModelId());
        if (imageSettings == null) throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片模型配置服务未就绪");
        String prompt = request.getPrompt().trim();
        if ("qwen-image".equals(model.id()) && prompt.length() > 800) {
            throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "千帆 Qwen-Image 提示词不能超过 800 字符");
        }
        String apiKey = imageSettings.resolveKey(userId, providerCode);
        long started = System.currentTimeMillis();
        boolean success = false;
        try {
            URI endpoint = "DASHSCOPE".equals(providerCode) ? aliNativeEndpoint() : providerEndpoint(model);
            HttpRequest call = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(providerRequestBody(model, prompt)))).build();
            HttpResponse<String> response = httpClient.send(call, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 401 || status == 403) throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片厂商密钥无效或没有该模型权限");
            if (status == 402 || status == 429) throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片厂商额度不足或请求过于频繁");
            if (status / 100 != 2) throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片厂商生成失败（HTTP " + status + "）");
            JsonNode body = objectMapper.readTree(response.body());
            String url = providerImageUrl(body, model.apiKind());
            URI imageUri = validImageUri(url, providerCode);
            HttpRequest download = HttpRequest.newBuilder(imageUri).timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<InputStream> image = httpClient.send(download, HttpResponse.BodyHandlers.ofInputStream());
            String contentType = image.headers().firstValue("Content-Type").orElse("").split(";")[0].trim().toLowerCase();
            String extension = switch (contentType) {
                case "image/png" -> ".png";
                case "image/jpeg" -> ".jpg";
                case "image/webp" -> ".webp";
                default -> "";
            };
            try (InputStream stream = image.body()) {
                if (image.statusCode() / 100 != 2 || extension.isEmpty())
                    throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片厂商返回的图片格式无效");
                byte[] bytes = stream.readNBytes(10 * 1024 * 1024 + 1);
                if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
                    throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片厂商返回的图片为空或超过 10 MB");
                String stored = minioUtils.uploadBytes(bytes, contentType, extension, "question-images/ai/");
                success = true;
                return ImageGenerationVO.builder().imageUrl(stored).source(providerCode).modelId(model.id())
                        .generationMs(System.currentTimeMillis() - started).build();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片厂商响应超时，请稍后重试");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片生成已中断");
        } catch (Exception e) {
            log.warn("Image provider generation failed provider={} errorType={}", providerCode, e.getClass().getSimpleName());
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片生成失败，请检查厂商服务或服务器网络");
        } finally {
            modelSettings.recordUsage(userId,
                    new AiModelSettingsService.ModelRoute(providerCode, model.id(), "", "", true),
                    "IMAGE_GEN", System.currentTimeMillis() - started, success);
        }
    }

    private URI aliNativeEndpoint() {
        URI configured = cloudEndpoint(); // validates the administrator-supplied DashScope host
        return URI.create("https://" + configured.getHost() + "/api/v1/services/aigc/multimodal-generation/generation");
    }

    static URI providerEndpoint(ImageProviderCatalog.ImageModel model) {
        return switch (model.apiKind()) {
            case "ALI_NATIVE" -> URI.create("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
            case "VOLC_IMAGE" -> URI.create("https://ark.cn-beijing.volces.com/api/v3/images/generations");
            case "BAIDU_GENERIC" -> URI.create("https://qianfan.baidubce.com/v2/images/generations");
            case "BAIDU_ERNIE" -> URI.create("https://qianfan.baidubce.com/v2/ernie-image/images/generations");
            case "SILICON_IMAGE" -> URI.create("https://api.siliconflow.cn/v1/images/generations");
            default -> throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "图片模型接口未接通");
        };
    }

    static ObjectNode providerRequestBody(ImageProviderCatalog.ImageModel model, String prompt) {
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("model", model.id());
        switch (model.apiKind()) {
            case "ALI_NATIVE" -> {
                body.putObject("input").putArray("messages").addObject().put("role", "user")
                        .putArray("content").addObject().put("text", prompt);
                body.putObject("parameters").put("size", "1024*1024").put("n", 1);
            }
            case "VOLC_IMAGE" -> body.put("prompt", prompt).put("size", "2K").put("response_format", "url");
            case "BAIDU_GENERIC" -> body.put("prompt", prompt).put("size", "1024x1024").put("n", 1);
            case "BAIDU_ERNIE" -> body.put("prompt", prompt).put("size", "1024x1024");
            case "SILICON_IMAGE" -> body.put("prompt", prompt).put("image_size",
                    "Qwen/Qwen-Image".equals(model.id()) ? "1328x1328" : "1024x1024");
            default -> throw new BusinessException(ResultCode.PARAMS_ERROR.getCode(), "图片模型接口未接通");
        }
        return body;
    }

    static String providerImageUrl(JsonNode body, String apiKind) {
        String url = switch (apiKind) {
            case "ALI_NATIVE" -> body.path("output").path("choices").path(0).path("message")
                    .path("content").path(0).path("image").asText();
            case "SILICON_IMAGE" -> body.path("images").path(0).path("url").asText();
            default -> body.path("data").path(0).path("url").asText();
        };
        if (url.isBlank()) throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片厂商未返回图片地址");
        return url;
    }

    static URI validImageUri(String url, String providerCode) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            boolean allowed = switch (providerCode) {
                case "DASHSCOPE" -> knownHost(host, "aliyuncs.com");
                case "VOLCENGINE" -> knownHost(host, "volces.com", "volccdn.com", "byteimg.com");
                case "BAIDU_QIANFAN" -> knownHost(host, "baidubce.com", "bcebos.com", "bdstatic.com");
                case "SILICONFLOW" -> knownHost(host, "siliconflow.cn", "siliconflow.com", "oss-cn-shanghai.aliyuncs.com");
                default -> false;
            };
            // Qianfan currently returns signed bcebos http URLs. Download only over TLS from its known host.
            if ("BAIDU_QIANFAN".equals(providerCode) && "http".equalsIgnoreCase(uri.getScheme())
                    && knownHost(host, "bcebos.com") && uri.getPort() == -1 && uri.getUserInfo() == null) {
                uri = URI.create("https://" + uri.getRawAuthority() + uri.getRawPath()
                        + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
            }
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || !allowed) throw new IllegalArgumentException();
            return uri;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "图片厂商返回了未获允许的下载地址");
        }
    }

    private static boolean knownHost(String host, String... suffixes) {
        for (String suffix : suffixes) if (host.equals(suffix) || host.endsWith("." + suffix)) return true;
        return false;
    }

    private URI cloudEndpoint() {
        URI base = URI.create(trimSlash(properties.getCloudBaseUrl()));
        String host = base.getHost();
        if (!"https".equalsIgnoreCase(base.getScheme()) || host == null
                || !("dashscope.aliyuncs.com".equals(host) || host.endsWith(".cn-beijing.maas.aliyuncs.com"))
                || !("/compatible-mode/v1".equals(base.getPath())
                    || "/api/v1/services/aigc/multimodal-generation/generation".equals(base.getPath()))
                || base.getPort() != -1
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼生图接口配置无效，请使用北京地域接口地址");
        }
        return "/compatible-mode/v1".equals(base.getPath()) ? URI.create(base + "/images/generations") : base;
    }

    static ObjectNode cloudRequestBody(String model, String prompt, boolean nativeApi) {
        ObjectNode body = new ObjectMapper().createObjectNode();
        body.put("model", model);
        if (nativeApi) {
            body.putObject("input").putArray("messages").addObject().put("role", "user")
                    .putArray("content").addObject().put("text", prompt);
            body.putObject("parameters").put("size", "1024*1024").put("n", 1);
        } else {
            body.put("prompt", prompt).put("size", "1024x1024").put("n", 1);
        }
        return body;
    }

    static String cloudImageUrl(JsonNode response, boolean nativeApi) {
        String url = nativeApi
                ? response.path("output").path("choices").path(0).path("message").path("content").path(0).path("image").asText()
                : response.path("data").path(0).path("url").asText();
        if (url.isBlank()) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "百炼未返回图片地址，请检查模型响应");
        }
        return url;
    }

    private JsonNode waitForImage(String baseUrl, String promptId) throws Exception {
        long deadline = System.currentTimeMillis() + properties.getTimeoutSeconds() * 1000L;
        while (System.currentTimeMillis() < deadline) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/history/" + encode(promptId)))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 == 2) {
                JsonNode task = objectMapper.readTree(response.body()).path(promptId);
                JsonNode status = task.path("status");
                if (status.path("completed").asBoolean(false)) {
                    JsonNode images = task.path("outputs").path("9").path("images");
                    if (images.isArray() && !images.isEmpty()) return images.get(0);
                    throw new IllegalStateException("ComfyUI 任务完成但没有输出图片");
                }
                if (status.path("status_str").asText().equals("error")) {
                    throw new IllegalStateException("ComfyUI 执行失败，请查看 ComfyUI 控制台");
                }
            }
            Thread.sleep(1000);
        }
        throw new IllegalStateException("ComfyUI 生成超时（" + properties.getTimeoutSeconds() + " 秒）");
    }

    private ObjectNode workflow(ImageGenerateRequest r, long seed, String checkpoint) {
        ObjectNode root = objectMapper.createObjectNode();
        node(root, "4", "CheckpointLoaderSimple").with("inputs").put("ckpt_name", checkpoint);
        ObjectNode positive = node(root, "6", "CLIPTextEncode").with("inputs");
        positive.put("text", r.getPrompt().trim() + ", realistic photo, clear separated objects, full objects in frame, good lighting");
        ref(positive, "clip", "4", 1);
        ObjectNode negative = node(root, "7", "CLIPTextEncode").with("inputs");
        negative.put("text", r.getNegativePrompt() == null || r.getNegativePrompt().isBlank() ? DEFAULT_NEGATIVE : r.getNegativePrompt());
        ref(negative, "clip", "4", 1);
        ObjectNode latent = node(root, "5", "EmptyLatentImage").with("inputs");
        latent.put("width", r.getWidth()).put("height", r.getHeight()).put("batch_size", 1);
        ObjectNode sampler = node(root, "3", "KSampler").with("inputs");
        sampler.put("seed", seed).put("steps", r.getSteps()).put("cfg", 7.0)
                .put("sampler_name", "euler").put("scheduler", "normal").put("denoise", 1.0);
        ref(sampler, "model", "4", 0); ref(sampler, "positive", "6", 0);
        ref(sampler, "negative", "7", 0); ref(sampler, "latent_image", "5", 0);
        ObjectNode decode = node(root, "8", "VAEDecode").with("inputs");
        ref(decode, "samples", "3", 0); ref(decode, "vae", "4", 2);
        ObjectNode save = node(root, "9", "SaveImage").with("inputs");
        save.put("filename_prefix", "mayue_oj"); ref(save, "images", "8", 0);
        return root;
    }

    private ObjectNode node(ObjectNode root, String id, String classType) {
        ObjectNode node = root.putObject(id); node.put("class_type", classType); node.putObject("inputs"); return node;
    }
    private void ref(ObjectNode inputs, String name, String node, int output) {
        inputs.putArray(name).add(node).add(output);
    }
    private HttpResponse<String> sendJson(String url, JsonNode body, int timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeout))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private void ensureSuccess(HttpResponse<String> response, String message) {
        if (response.statusCode() / 100 != 2) {
            String body = response.body();
            throw new IllegalStateException(message + " HTTP " + response.statusCode() + (body == null ? "" : ": " + body.substring(0, Math.min(300, body.length()))));
        }
    }
    private static String trimSlash(String value) { return value == null ? "" : value.trim().replaceAll("/+$", ""); }
    private static String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
}
