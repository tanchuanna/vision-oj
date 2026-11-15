package com.spingbootinit.config;

import com.spingbootinit.common.iterceptor.JwtInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WebMvcConfigTest {
    private static class InspectableRegistry extends InterceptorRegistry {
        List<Object> interceptors() { return getInterceptors(); }
    }

    @Test
    void imageModelEndpointsRequireJwtInterceptor() {
        WebMvcConfig config = new WebMvcConfig();
        ReflectionTestUtils.setField(config, "jwtInterceptor", mock(JwtInterceptor.class));
        InspectableRegistry registry = new InspectableRegistry();
        config.addInterceptors(registry);

        for (String path : new String[]{"/ai/image-models/catalog", "/ai/image-models/mine/DASHSCOPE/test"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            ServletRequestPathUtils.parseAndCache(request);
            assertTrue(registry.interceptors().stream()
                    .filter(MappedInterceptor.class::isInstance)
                    .map(MappedInterceptor.class::cast)
                    .anyMatch(interceptor -> interceptor.matches(request)), path);
        }
    }
}
