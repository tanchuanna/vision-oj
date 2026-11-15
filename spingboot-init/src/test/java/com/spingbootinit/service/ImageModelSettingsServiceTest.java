package com.spingbootinit.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ImageModelSettingsServiceTest {
    @Test
    void savesOnlyEncryptedPersonalImageKey() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AiModelSettingsService textSettings = mock(AiModelSettingsService.class);
        ImageModelSettingsService service = new ImageModelSettingsService(jdbc, textSettings);
        when(jdbc.query(startsWith("SELECT key_cipher"), any(RowMapper.class), eq(7L), eq("VOLCENGINE"))).thenReturn(List.of());
        when(textSettings.encryptImageCredential(7L, "VOLCENGINE", "private-key-1234")).thenReturn("encrypted-value");

        service.save(7L, "VOLCENGINE", "doubao-seedream-5-0-flash-260915", "private-key-1234");

        ArgumentCaptor<String> cipher = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(startsWith("INSERT INTO user_ai_image_config"), eq(7L), eq("VOLCENGINE"),
                eq("doubao-seedream-5-0-flash-260915"), cipher.capture(), eq("1234"));
        assertEquals("encrypted-value", cipher.getValue());
    }
}
