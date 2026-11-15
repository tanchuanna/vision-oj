package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import com.spingbootinit.config.BailianAssistProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 每个账号单独保存模型凭据。接口地址只从服务端预设读取。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiModelSettingsService {
    private static final Pattern MODEL_ID = Pattern.compile("[A-Za-z0-9_./:-]{1,100}");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, Provider> PROVIDERS = Map.ofEntries(
            Map.entry("DASHSCOPE", new Provider("DASHSCOPE", "阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", List.of("qwen-plus", "qwen-turbo", "qwen3-vl-plus"))),
            Map.entry("DEEPSEEK", new Provider("DEEPSEEK", "DeepSeek", "https://api.deepseek.com", List.of("deepseek-flash", "deepseek-v4-pro"))),
            Map.entry("SILICONFLOW", new Provider("SILICONFLOW", "硅基流动", "https://api.siliconflow.cn/v1", List.of("deepseek-ai/DeepSeek-V4-Flash", "Qwen/Qwen3-32B", "Pro/moonshotai/Kimi-K2.6"))),
            Map.entry("VOLCENGINE", new Provider("VOLCENGINE", "火山方舟", "https://ark.cn-beijing.volces.com/api/v3", List.of("doubao-seed-2-1-pro-260915"))),
            Map.entry("BAIDU_QIANFAN", new Provider("BAIDU_QIANFAN", "百度千帆", "https://qianfan.baidubce.com/v2", List.of("ernie-4.5-turbo-32k"))),
            Map.entry("ZHIPU", new Provider("ZHIPU", "智谱开放平台", "https://open.bigmodel.cn/api/paas/v4", List.of("glm-4.5"))),
            Map.entry("STEPFUN", new Provider("STEPFUN", "阶跃星辰", "https://api.stepfun.com/v1", List.of("step-3.7-flash"))),
            Map.entry("MOONSHOT", new Provider("MOONSHOT", "Kimi 开放平台", "https://api.moonshot.cn/v1", List.of("kimi-k2.5"))),
            Map.entry("TENCENT_TOKENHUB", new Provider("TENCENT_TOKENHUB", "腾讯云 TokenHub", "https://tokenhub.tencentmaas.com/v1",
                    List.of("hy4", "hy3", "deepseek-v4.1-flash", "glm-5.3-flash", "kimi-k2.6", "minimax-m3", "mimo-v2.6-flash"))));
    private static final Map<String, List<String>> VISION_MODELS = Map.of(
            "DASHSCOPE", List.of("qwen3-vl-plus", "qwen3-vl-flash"),
            "DEEPSEEK", List.of("deepseek-flash"),
            "SILICONFLOW", List.of("Pro/moonshotai/Kimi-K2.6"));

    private final JdbcTemplate jdbcTemplate;
    private final BailianAssistProperties siteProperties;

    @Value("${ai.personal-key-encryption-key:}")
    private String encryptionKey;

    public record Provider(String code, String name, String baseUrl, List<String> models) {}
    public record ModelRoute(String providerCode, String modelId, String baseUrl, String apiKey, boolean personal) {}
    public record SavedConfig(String providerCode, String modelId, String keyMask) {}

    public Map<String, Object> catalog() {
        List<Map<String, Object>> providers = new ArrayList<>();
        for (String code : List.of("DASHSCOPE", "DEEPSEEK", "MOONSHOT", "VOLCENGINE", "BAIDU_QIANFAN", "ZHIPU", "STEPFUN", "SILICONFLOW", "TENCENT_TOKENHUB")) {
            Provider p = PROVIDERS.get(code);
            providers.add(Map.of("code", p.code(), "name", p.name(), "models", p.models(),
                    "visionModels", visionModelsFor(code)));
        }
        return Map.of("siteDefaultAvailable", siteProperties.isEnabled() && StringUtils.hasText(siteProperties.getApiKey()),
                "siteDefaultModel", siteProperties.getModel(),
                "providers", providers);
    }

    public static List<String> visionModelsFor(String providerCode) {
        return VISION_MODELS.getOrDefault(providerCode, List.of());
    }

    public static boolean supportsVision(ModelRoute route) {
        String code = "SITE_DEFAULT".equals(route.providerCode()) ? "DASHSCOPE" : route.providerCode();
        return visionModelsFor(code).contains(route.modelId());
    }

    public List<SavedConfig> list(long userId) {
        try {
            return jdbcTemplate.query("SELECT provider_code, model_id, key_last4 FROM user_ai_provider_config WHERE user_id = ? ORDER BY provider_code",
                    (rs, row) -> new SavedConfig(rs.getString(1), rs.getString(2), "••••" + rs.getString(3)), userId);
        } catch (DataAccessException e) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "个人模型配置表不可用，请管理员检查数据库权限或执行 db/schema.sql");
        }
    }

    public void save(long userId, String providerCode, String modelId, String apiKey) {
        Provider provider = requireProvider(providerCode);
        String model = requireModel(modelId);
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.isEmpty()) {
            List<String> existing = jdbcTemplate.query("SELECT key_cipher FROM user_ai_provider_config WHERE user_id = ? AND provider_code = ?",
                    (rs, row) -> rs.getString(1), userId, provider.code());
            if (existing.isEmpty()) throw badRequest("首次保存请填写 API Key");
            jdbcTemplate.update("UPDATE user_ai_provider_config SET model_id = ? WHERE user_id = ? AND provider_code = ?",
                    model, userId, provider.code());
            return;
        }
        if (key.length() > 512 || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) throw badRequest("API Key 格式不正确");
        String cipher = encrypt(userId, provider.code(), key);
        String last4 = key.substring(Math.max(0, key.length() - 4));
        jdbcTemplate.update("INSERT INTO user_ai_provider_config (user_id, provider_code, model_id, key_cipher, key_last4) " +
                        "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE model_id = VALUES(model_id), key_cipher = VALUES(key_cipher), key_last4 = VALUES(key_last4)",
                userId, provider.code(), model, cipher, last4);
    }

    public void delete(long userId, String providerCode) {
        Provider provider = requireProvider(providerCode);
        jdbcTemplate.update("DELETE FROM user_ai_provider_config WHERE user_id = ? AND provider_code = ?", userId, provider.code());
    }

    /** 注销账号后清除可解密的个人凭据；旧库未迁移时不阻断原有注销流程。 */
    public void deleteAllForClosedAccount(long userId) {
        try {
            jdbcTemplate.update("DELETE FROM user_ai_provider_config WHERE user_id = ?", userId);
        } catch (DataAccessException e) {
            log.warn("Closed account AI credential cleanup failed userId={} errorType={}", userId, e.getClass().getSimpleName());
        }
    }

    public ModelRoute resolve(long userId, String providerCode, String requestedModel) {
        if (!StringUtils.hasText(providerCode) || "SITE_DEFAULT".equalsIgnoreCase(providerCode.trim())) {
            if (!siteProperties.isEnabled() || !StringUtils.hasText(siteProperties.getApiKey())) {
                throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "站点默认 AI 未配置，请在账号设置中填写自己的 API Key");
            }
            // 站点付费的模型固定由管理员控制，忽略客户端的模型名。
            return new ModelRoute("SITE_DEFAULT", siteProperties.getModel(), siteProperties.getBaseUrl(), siteProperties.getApiKey(), false);
        }
        Provider provider = requireProvider(providerCode);
        List<String[]> rows = jdbcTemplate.query("SELECT model_id, key_cipher FROM user_ai_provider_config WHERE user_id = ? AND provider_code = ?",
                (rs, row) -> new String[]{rs.getString(1), rs.getString(2)}, userId, provider.code());
        if (rows.isEmpty()) throw badRequest("当前账号尚未配置该模型平台，请先到账号设置中保存 API Key");
        String model = StringUtils.hasText(requestedModel) ? requireModel(requestedModel) : rows.get(0)[0];
        return new ModelRoute(provider.code(), model, provider.baseUrl(), decrypt(userId, provider.code(), rows.get(0)[1]), true);
    }

    public void test(long userId, String providerCode, String modelId) {
        ModelRoute route = resolve(userId, providerCode, modelId);
        if (!route.personal()) throw badRequest("请选择已保存的个人模型平台进行连接测试");
        try {
            String body = "{\"model\":\"" + route.modelId() + "\",\"max_tokens\":8,\"messages\":[{\"role\":\"user\",\"content\":\"Hi\"}]}";
            HttpRequest request = HttpRequest.newBuilder(URI.create(route.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + route.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            int status = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
                    .send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status < 200 || status >= 300) throw badRequest(connectionFailure(route.providerCode(), status));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "连接模型平台失败，请检查网络后重试");
        }
    }

    static String connectionFailure(String providerCode, int status) {
        boolean deepSeek = "DEEPSEEK".equals(providerCode);
        String providerName = PROVIDERS.containsKey(providerCode) ? PROVIDERS.get(providerCode).name() : "模型平台";
        return switch (status) {
            case 401 -> deepSeek
                    ? "DeepSeek 密钥认证失败（401）：请在 DeepSeek 平台创建新 API Key，保存新密钥后重试"
                    : providerName + "密钥认证失败（401）：请核对 API Key、接口地域和密钥类型";
            case 403 -> deepSeek
                    ? "DeepSeek 拒绝访问（403）：请检查账号状态与模型访问权限"
                    : providerName + "拒绝访问（403）：请检查服务开通状态和 API Key 的模型权限";
            case 402 -> deepSeek ? "DeepSeek 账号余额不足（402）" : "模型平台额度不足（402）";
            case 429 -> "模型平台请求过于频繁（429），请稍后重试";
            case 400, 404, 422 -> "模型名称无效或当前账号不支持该模型（HTTP " + status + "）";
            default -> "模型平台暂不可用（HTTP " + status + "）";
        };
    }

    public void recordUsage(long userId, ModelRoute route, String feature, long elapsedMs, boolean success) {
        try {
            jdbcTemplate.update("INSERT INTO ai_usage_log (user_id, provider_code, model_id, feature, duration_ms, success) VALUES (?, ?, ?, ?, ?, ?)",
                    userId, route.providerCode(), route.modelId(), feature, Math.max(0, elapsedMs), success ? 1 : 0);
        } catch (DataAccessException ignored) {
            // 老数据库未执行新增表迁移时不影响原有 AI 功能。
        }
    }

    private static Provider requireProvider(String code) {
        Provider provider = code == null ? null : PROVIDERS.get(code.trim().toUpperCase());
        if (provider == null) throw badRequest("请选择受支持的模型平台");
        return provider;
    }

    private static String requireModel(String modelId) {
        String value = modelId == null ? "" : modelId.trim();
        if (!MODEL_ID.matcher(value).matches()) throw badRequest("模型名称只能包含字母、数字、点、横线、下划线、斜杠或冒号，最多 100 字符");
        return value;
    }

    private byte[] keyBytes() {
        try {
            byte[] key = Base64.getDecoder().decode(encryptionKey == null ? "" : encryptionKey.trim());
            if (key.length == 32) return key;
        } catch (IllegalArgumentException ignored) { }
        throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "个人 API Key 加密功能未配置，请联系管理员设置 AI_CREDENTIAL_ENCRYPTION_KEY");
    }

    private String encrypt(long userId, String provider, String raw) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD((userId + ":" + provider).getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(raw.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(encrypted, 0, all, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "加密 API Key 失败");
        }
    }

    String encryptImageCredential(long userId, String provider, String raw) {
        return encrypt(userId, "IMAGE:" + provider, raw);
    }

    String decryptImageCredential(long userId, String provider, String encoded) {
        return decrypt(userId, "IMAGE:" + provider, encoded);
    }

    private String decrypt(long userId, String provider, String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length < 29) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes(), "AES"), new GCMParameterSpec(128, all, 0, 12));
            cipher.updateAAD((userId + ":" + provider).getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.OPERATION_ERROR.getCode(), "个人 API Key 无法解密，请联系管理员检查加密密钥");
        }
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(ResultCode.PARAMS_ERROR.getCode(), message);
    }
}
