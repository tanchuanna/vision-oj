package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import com.spingbootinit.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** Per-user image credentials; DashScope can fall back to the user's existing text credential. */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImageModelSettingsService {
    private final JdbcTemplate jdbcTemplate;
    private final AiModelSettingsService textSettings;

    public record SavedImageConfig(String providerCode, String modelId, String keyMask, String credentialSource) {}
    private record Stored(String providerCode, String modelId, String cipher, String last4) {}

    public List<SavedImageConfig> list(long userId) {
        List<Stored> rows = jdbcTemplate.query(
                "SELECT provider_code, model_id, key_cipher, key_last4 FROM user_ai_image_config WHERE user_id = ? ORDER BY provider_code",
                (rs, index) -> new Stored(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), userId);
        List<SavedImageConfig> result = new ArrayList<>();
        String sharedMask = sharedDashScopeMask(userId);
        for (Stored row : rows) {
            if (row.cipher() != null && !row.cipher().isBlank()) {
                result.add(new SavedImageConfig(row.providerCode(), row.modelId(), mask(row.last4()), "IMAGE"));
            } else if ("DASHSCOPE".equals(row.providerCode()) && sharedMask != null) {
                result.add(new SavedImageConfig(row.providerCode(), row.modelId(), sharedMask, "TEXT_SHARED"));
            }
        }
        if (sharedMask != null && result.stream().noneMatch(item -> "DASHSCOPE".equals(item.providerCode()))) {
            result.add(new SavedImageConfig("DASHSCOPE", ImageProviderCatalog.requireProvider("DASHSCOPE").models().get(0).id(),
                    sharedMask, "TEXT_SHARED"));
        }
        return result;
    }

    public void save(long userId, String providerCode, String modelId, String apiKey) {
        ImageProviderCatalog.requireModel(providerCode, modelId);
        String key = apiKey == null ? "" : apiKey.trim();
        if (key.length() > 512 || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) throw invalid("API Key 格式不正确");
        List<String> existing = jdbcTemplate.query(
                "SELECT key_cipher FROM user_ai_image_config WHERE user_id = ? AND provider_code = ?",
                (rs, index) -> rs.getString(1), userId, providerCode);
        if (key.isEmpty() && existing.stream().noneMatch(StringUtils::hasText)
                && !("DASHSCOPE".equals(providerCode) && sharedDashScopeMask(userId) != null)) {
            throw invalid("首次配置请填写图片厂商 API Key");
        }
        String cipher = key.isEmpty() ? null : textSettings.encryptImageCredential(userId, providerCode, key);
        String last4 = key.isEmpty() ? null : key.substring(Math.max(0, key.length() - 4));
        jdbcTemplate.update("INSERT INTO user_ai_image_config (user_id, provider_code, model_id, key_cipher, key_last4) "
                        + "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE model_id = VALUES(model_id), "
                        + "key_cipher = COALESCE(VALUES(key_cipher), key_cipher), key_last4 = COALESCE(VALUES(key_last4), key_last4)",
                userId, providerCode, modelId, cipher, last4);
    }

    public void delete(long userId, String providerCode) {
        ImageProviderCatalog.requireProvider(providerCode);
        jdbcTemplate.update("DELETE FROM user_ai_image_config WHERE user_id = ? AND provider_code = ?", userId, providerCode);
    }

    public String resolveKey(long userId, String providerCode) {
        ImageProviderCatalog.requireProvider(providerCode);
        List<String> ciphers = jdbcTemplate.query(
                "SELECT key_cipher FROM user_ai_image_config WHERE user_id = ? AND provider_code = ?",
                (rs, index) -> rs.getString(1), userId, providerCode);
        if (!ciphers.isEmpty() && StringUtils.hasText(ciphers.get(0))) {
            return textSettings.decryptImageCredential(userId, providerCode, ciphers.get(0));
        }
        if ("DASHSCOPE".equals(providerCode) && sharedDashScopeMask(userId) != null) {
            return textSettings.resolve(userId, "DASHSCOPE", null).apiKey();
        }
        throw invalid("当前账号尚未配置该图片厂商的 API Key");
    }

    public void deleteAllForClosedAccount(long userId) {
        try {
            jdbcTemplate.update("DELETE FROM user_ai_image_config WHERE user_id = ?", userId);
        } catch (DataAccessException e) {
            log.warn("Could not clear image model credentials for closed account; userId={} errorType={}",
                    userId, e.getClass().getSimpleName());
        }
    }

    private String sharedDashScopeMask(long userId) {
        List<String> masks = jdbcTemplate.query("SELECT key_last4 FROM user_ai_provider_config WHERE user_id = ? AND provider_code = 'DASHSCOPE'",
                (rs, index) -> rs.getString(1), userId);
        return masks.isEmpty() || !StringUtils.hasText(masks.get(0)) ? null : mask(masks.get(0));
    }

    private static String mask(String last4) { return "••••" + last4; }
    private static BusinessException invalid(String message) {
        return new BusinessException(ResultCode.PARAMS_ERROR.getCode(), message);
    }
}
