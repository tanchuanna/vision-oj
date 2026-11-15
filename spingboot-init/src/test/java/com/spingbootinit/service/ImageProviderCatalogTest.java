package com.spingbootinit.service;

import com.spingbootinit.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageProviderCatalogTest {
    @Test
    void listsOnlyImplementedDomesticImageProvidersAndModels() {
        assertEquals(4, ImageProviderCatalog.providers().size());
        assertEquals("DASHSCOPE", ImageProviderCatalog.requireModel("DASHSCOPE", "qwen-image-2.0-pro").providerCode());
        assertEquals("VOLCENGINE", ImageProviderCatalog.requireModel("VOLCENGINE", "doubao-seedream-5-0-flash-260915").providerCode());
        assertEquals("BAIDU_QIANFAN", ImageProviderCatalog.requireModel("BAIDU_QIANFAN", "ernie-image-turbo").providerCode());
        assertEquals("SILICONFLOW", ImageProviderCatalog.requireModel("SILICONFLOW", "Kwai-Kolors/Kolors").providerCode());
        assertThrows(BusinessException.class, () -> ImageProviderCatalog.requireModel("VOLCENGINE", "qwen-image-2.0-pro"));
    }
}
