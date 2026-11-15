package com.spingbootinit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.config.ImageGenerationProperties;
import com.spingbootinit.model.dto.vision.ImageGenerateRequest;
import com.spingbootinit.service.AiModelSettingsService;
import com.spingbootinit.service.ImageModelSettingsService;
import com.spingbootinit.service.ImageProviderCatalog;
import com.spingbootinit.utils.MinioUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ImageGenerationServiceTest {
    private final HttpClient client = mock(HttpClient.class);
    private final MinioUtils minio = mock(MinioUtils.class);
    private final AiModelSettingsService settings = mock(AiModelSettingsService.class);
    private final ImageGenerationService service = new ImageGenerationService(
            new ImageGenerationProperties(), new ObjectMapper(), minio, settings, client);

    private ImageGenerateRequest request(String source, String model) {
        ImageGenerateRequest request = new ImageGenerateRequest();
        request.setPrompt("a clear street scene");
        request.setSource(source);
        request.setModelId(model);
        return request;
    }

    @Test
    void rejectsUnlistedCloudModelBeforeLookingUpKey() {
        assertThrows(BusinessException.class, () -> service.generate(request("DASHSCOPE", "some-other-model"), 7L));
        verifyNoInteractions(settings, client, minio);
    }

    @Test
    void rejectsNonBeijingCloudEndpoint() {
        ImageGenerationProperties properties = new ImageGenerationProperties();
        properties.setCloudBaseUrl("https://example.com/compatible-mode/v1");
        ImageGenerationService configured = new ImageGenerationService(properties, new ObjectMapper(), minio, settings, client);
        when(settings.resolve(7L, "DASHSCOPE", null)).thenReturn(
                new AiModelSettingsService.ModelRoute("DASHSCOPE", "qwen-plus", "", "teacher-secret", true));
        assertThrows(BusinessException.class,
                () -> configured.generate(request("DASHSCOPE", "qwen-image-2.1-turbo"), 7L));
        verifyNoInteractions(client, minio);
    }

    @Test
    void oldRequestWithoutSourceStillGeneratesUsingLocalCheckpoint() throws Exception {
        HttpResponse<String> catalog = mock(HttpResponse.class);
        when(catalog.body()).thenReturn("{\"CheckpointLoaderSimple\":{\"input\":{\"required\":{\"ckpt_name\":[[\"v1-5-pruned-emaonly.safetensors\"]]}}}}");
        HttpResponse<String> queued = mock(HttpResponse.class);
        when(queued.statusCode()).thenReturn(200);
        when(queued.body()).thenReturn("{\"prompt_id\":\"abc\"}");
        HttpResponse<String> history = mock(HttpResponse.class);
        when(history.statusCode()).thenReturn(200);
        when(history.body()).thenReturn("{\"abc\":{\"status\":{\"completed\":true},\"outputs\":{\"9\":{\"images\":[{\"filename\":\"a.png\",\"type\":\"output\"}]}}}}");
        HttpResponse<byte[]> image = mock(HttpResponse.class);
        when(image.statusCode()).thenReturn(200);
        when(image.body()).thenReturn(new byte[]{1, 2});
        doReturn(catalog, queued, history, image).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(minio.uploadBytes(any(byte[].class), eq("image/png"), eq(".png"), eq("question-images/ai/")))
                .thenReturn("https://minio.example/a.png");
        var result = service.generate(request(null, null), 7L);
        assertEquals("LOCAL_COMFYUI", result.getSource());
        assertEquals("v1-5-pruned-emaonly.safetensors", result.getModelId());
        assertEquals("https://minio.example/a.png", result.getImageUrl());
        verifyNoInteractions(settings);
    }

    @Test
    void localModelMustBeInstalledAndAllowed() throws Exception {
        HttpResponse<String> catalog = mock(HttpResponse.class);
        when(catalog.body()).thenReturn("{\"CheckpointLoaderSimple\":{\"input\":{\"required\":{\"ckpt_name\":[[\"other.safetensors\"]]}}}}");
        doReturn(catalog).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        assertThrows(BusinessException.class, () -> service.generate(request("LOCAL_COMFYUI", "other.safetensors"), 7L));
        verifyNoInteractions(settings, minio);
    }

    @Test
    void cloudGenerationUsesOnlyTeachersKeyAndStoresImageInMinio() throws Exception {
        when(settings.resolve(7L, "DASHSCOPE", null)).thenReturn(
                new AiModelSettingsService.ModelRoute("DASHSCOPE", "qwen-plus", "", "teacher-secret", true));
        HttpResponse<String> generated = mock(HttpResponse.class);
        when(generated.statusCode()).thenReturn(200);
        when(generated.body()).thenReturn("{\"data\":[{\"url\":\"https://example.aliyuncs.com/image.png\"}]}");
        HttpResponse<byte[]> downloaded = mock(HttpResponse.class);
        when(downloaded.statusCode()).thenReturn(200);
        when(downloaded.body()).thenReturn(new byte[]{1, 2, 3});
        when(downloaded.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (a, b) -> true));
        doReturn(generated, downloaded).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(minio.uploadBytes(any(byte[].class), eq("image/png"), eq(".png"), eq("question-images/ai/")))
                .thenReturn("https://minio.example/question-images/ai/generated.png");

        var result = service.generate(request("DASHSCOPE", "qwen-image-2.1-turbo"), 7L);
        assertEquals("https://minio.example/question-images/ai/generated.png", result.getImageUrl());
        assertEquals("DASHSCOPE", result.getSource());
        verify(settings).resolve(7L, "DASHSCOPE", null);
        verify(minio).uploadBytes(new byte[]{1, 2, 3}, "image/png", ".png", "question-images/ai/");
        verify(client, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void productionNativeCloudEndpointUsesNativePayloadAndResponse() throws Exception {
        ImageGenerationProperties properties = new ImageGenerationProperties();
        properties.setCloudBaseUrl("https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation");
        ImageGenerationService configured = new ImageGenerationService(properties, new ObjectMapper(), minio, settings, client);
        when(settings.resolve(7L, "DASHSCOPE", null)).thenReturn(
                new AiModelSettingsService.ModelRoute("DASHSCOPE", "qwen-plus", "", "teacher-secret", true));
        HttpResponse<String> generated = mock(HttpResponse.class);
        when(generated.statusCode()).thenReturn(200);
        when(generated.body()).thenReturn("{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"https://example.aliyuncs.com/image.png\"}]}}]}}");
        HttpResponse<byte[]> downloaded = mock(HttpResponse.class);
        when(downloaded.statusCode()).thenReturn(200);
        when(downloaded.body()).thenReturn(new byte[]{1, 2, 3});
        when(downloaded.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (a, b) -> true));
        doReturn(generated, downloaded).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(minio.uploadBytes(any(byte[].class), eq("image/png"), eq(".png"), eq("question-images/ai/")))
                .thenReturn("https://minio.example/generated.png");

        var result = configured.generate(request("DASHSCOPE", "qwen-image-2.1-turbo"), 7L);
        assertEquals("https://minio.example/generated.png", result.getImageUrl());
        var payload = ImageGenerationService.cloudRequestBody("qwen-image-2.1-turbo", "a clear street scene", true);
        assertEquals("a clear street scene", payload.path("input").path("messages").path(0).path("content").path(0).path("text").asText());
        assertEquals("1024*1024", payload.path("parameters").path("size").asText());
        var sent = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(client, times(2)).send(sent.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("/api/v1/services/aigc/multimodal-generation/generation", sent.getAllValues().get(0).uri().getPath());
    }

    @Test
    void providerAdaptersUseDistinctOfficialEndpointsAndPayloads() throws Exception {
        var volc = ImageGenerationService.providerRequestBody(
                ImageProviderCatalog.requireModel("VOLCENGINE", "doubao-seedream-5-0-flash-260915"), "a street scene");
        assertEquals("a street scene", volc.path("prompt").asText());
        assertEquals("2K", volc.path("size").asText());

        var baidu = ImageGenerationService.providerRequestBody(
                ImageProviderCatalog.requireModel("BAIDU_QIANFAN", "ernie-image-turbo"), "a street scene");
        assertEquals("ernie-image-turbo", baidu.path("model").asText());
        assertEquals("/v2/ernie-image/images/generations",
                ImageGenerationService.providerEndpoint(ImageProviderCatalog.requireModel("BAIDU_QIANFAN", "ernie-image-turbo")).getPath());
        assertEquals("/api/v3/images/generations",
                ImageGenerationService.providerEndpoint(ImageProviderCatalog.requireModel("VOLCENGINE", "doubao-seedream-5-0-flash-260915")).getPath());

        var silicon = ImageProviderCatalog.requireModel("SILICONFLOW", "Kwai-Kolors/Kolors");
        assertEquals("/v1/images/generations", ImageGenerationService.providerEndpoint(silicon).getPath());
        assertEquals("1024x1024", ImageGenerationService.providerRequestBody(silicon, "a street scene").path("image_size").asText());
        assertEquals("https://example.siliconflow.cn/output.png",
                ImageGenerationService.providerImageUrl(new ObjectMapper().readTree("{\"images\":[{\"url\":\"https://example.siliconflow.cn/output.png\"}]}"), "SILICON_IMAGE"));
    }

    @Test
    void rejectsDownloadUrlOutsideVendorsKnownDomains() {
        assertThrows(BusinessException.class,
                () -> ImageGenerationService.validImageUri("https://127.0.0.1/private", "BAIDU_QIANFAN"));
        assertThrows(BusinessException.class,
                () -> ImageGenerationService.validImageUri("https://attacker.example/image.png", "VOLCENGINE"));
        assertEquals("https://qianfan-img-gen.bj.bcebos.com/image.png?authorization=abc",
                ImageGenerationService.validImageUri(
                        "http://qianfan-img-gen.bj.bcebos.com/image.png?authorization=abc", "BAIDU_QIANFAN").toString());
        assertThrows(BusinessException.class,
                () -> ImageGenerationService.validImageUri("http://example.siliconflow.cn/output.png", "SILICONFLOW"));
        assertEquals("https://sf-maas-prod.oss-cn-shanghai.aliyuncs.com/output.png",
                ImageGenerationService.validImageUri("https://sf-maas-prod.oss-cn-shanghai.aliyuncs.com/output.png", "SILICONFLOW").toString());
    }

    @Test
    void siliconFlowImageUsesItsOwnResponseAndUploadsToMinio() throws Exception {
        ImageModelSettingsService imageSettings = mock(ImageModelSettingsService.class);
        ImageGenerationService configured = new ImageGenerationService(new ImageGenerationProperties(),
                new ObjectMapper(), minio, settings, imageSettings, client);
        when(imageSettings.resolveKey(7L, "SILICONFLOW")).thenReturn("personal-key");
        HttpResponse<String> generated = mock(HttpResponse.class);
        when(generated.statusCode()).thenReturn(200);
        when(generated.body()).thenReturn("{\"images\":[{\"url\":\"https://example.siliconflow.cn/output.png\"}]}");
        HttpResponse<java.io.InputStream> downloaded = mock(HttpResponse.class);
        when(downloaded.statusCode()).thenReturn(200);
        when(downloaded.body()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(downloaded.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (a, b) -> true));
        doReturn(generated, downloaded).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(minio.uploadBytes(any(byte[].class), eq("image/png"), eq(".png"), eq("question-images/ai/")))
                .thenReturn("https://minio.example/generated.png");

        var result = configured.generate(request("SILICONFLOW", "Kwai-Kolors/Kolors"), 7L);
        assertEquals("SILICONFLOW", result.getSource());
        assertEquals("https://minio.example/generated.png", result.getImageUrl());
        verify(imageSettings).resolveKey(7L, "SILICONFLOW");
        verify(minio).uploadBytes(new byte[]{1, 2, 3}, "image/png", ".png", "question-images/ai/");
    }

    @Test
    void volcengineGenerationUsesPersonalKeyAndStoresBoundedImage() throws Exception {
        ImageModelSettingsService imageSettings = mock(ImageModelSettingsService.class);
        ImageGenerationService configured = new ImageGenerationService(new ImageGenerationProperties(),
                new ObjectMapper(), minio, settings, imageSettings, client);
        when(imageSettings.resolveKey(7L, "VOLCENGINE")).thenReturn("personal-key");
        HttpResponse<String> generated = mock(HttpResponse.class);
        when(generated.statusCode()).thenReturn(200);
        when(generated.body()).thenReturn("{\"data\":[{\"url\":\"https://image.volces.com/generated.png\"}]}");
        HttpResponse<java.io.InputStream> downloaded = mock(HttpResponse.class);
        when(downloaded.statusCode()).thenReturn(200);
        when(downloaded.body()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
        when(downloaded.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (a, b) -> true));
        doReturn(generated, downloaded).when(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        when(minio.uploadBytes(any(byte[].class), eq("image/png"), eq(".png"), eq("question-images/ai/")))
                .thenReturn("https://minio.example/generated.png");

        var result = configured.generate(request("VOLCENGINE", "doubao-seedream-5-0-flash-260915"), 7L);
        assertEquals("https://minio.example/generated.png", result.getImageUrl());
        assertEquals("VOLCENGINE", result.getSource());
        verify(imageSettings).resolveKey(7L, "VOLCENGINE");
        verify(minio).uploadBytes(new byte[]{1, 2, 3}, "image/png", ".png", "question-images/ai/");
    }

    @Test
    void aliAndBaiduAdaptersAcceptTheirOwnResponsesAndStoreImages() throws Exception {
        for (String provider : List.of("DASHSCOPE", "BAIDU_QIANFAN")) {
            HttpClient providerClient = mock(HttpClient.class);
            MinioUtils providerMinio = mock(MinioUtils.class);
            ImageModelSettingsService imageSettings = mock(ImageModelSettingsService.class);
            ImageGenerationService configured = new ImageGenerationService(new ImageGenerationProperties(),
                    new ObjectMapper(), providerMinio, settings, imageSettings, providerClient);
            when(imageSettings.resolveKey(7L, provider)).thenReturn("private-key");
            String model = "DASHSCOPE".equals(provider) ? "qwen-image-2.0-pro" : "ernie-image-turbo";
            String url = "DASHSCOPE".equals(provider)
                    ? "https://oss.aliyuncs.com/generated.png"
                    : "http://qianfan-img-gen.bj.bcebos.com/generated.png";
            String body = "DASHSCOPE".equals(provider)
                    ? "{\"output\":{\"choices\":[{\"message\":{\"content\":[{\"image\":\"" + url + "\"}]}}]}}"
                    : "{\"data\":[{\"url\":\"" + url + "\"}]}";
            HttpResponse<String> generated = mock(HttpResponse.class);
            when(generated.statusCode()).thenReturn(200);
            when(generated.body()).thenReturn(body);
            HttpResponse<java.io.InputStream> downloaded = mock(HttpResponse.class);
            when(downloaded.statusCode()).thenReturn(200);
            when(downloaded.body()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));
            when(downloaded.headers()).thenReturn(HttpHeaders.of(Map.of("Content-Type", List.of("image/png")), (a, b) -> true));
            doReturn(generated, downloaded).when(providerClient).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
            when(providerMinio.uploadBytes(any(byte[].class), eq("image/png"), eq(".png"), eq("question-images/ai/")))
                    .thenReturn("https://minio.example/generated.png");

            assertEquals("https://minio.example/generated.png", configured.generate(request(provider, model), 7L).getImageUrl());
            verify(imageSettings).resolveKey(7L, provider);
            verify(providerMinio).uploadBytes(new byte[]{1, 2, 3}, "image/png", ".png", "question-images/ai/");
        }
    }
}
