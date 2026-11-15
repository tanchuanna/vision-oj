package com.spingbootinit.service.impl;

import com.spingbootinit.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserStatusNotifyServiceTest {

    @Test
    void shutdownCompletesAndRemovesActiveEmitters() throws Exception {
        RedisUtil redisUtil = mock(RedisUtil.class);
        when(redisUtil.get(anyString())).thenReturn(null);
        UserStatusNotifyServiceImpl service = new UserStatusNotifyServiceImpl();
        ReflectionTestUtils.setField(service, "redisUtil", redisUtil);

        SseEmitter emitter = service.subscribe(7L);
        Map<?, ?> before = (Map<?, ?>) ReflectionTestUtils.getField(service, "emitters");
        assertThat(before).hasSize(1);

        service.closeAllEmitters();

        Map<?, ?> after = (Map<?, ?>) ReflectionTestUtils.getField(service, "emitters");
        assertThat(after).isEmpty();
        assertThatThrownBy(() -> emitter.send(SseEmitter.event().name("status").data("1")))
                .isInstanceOf(IllegalStateException.class);
    }
}
