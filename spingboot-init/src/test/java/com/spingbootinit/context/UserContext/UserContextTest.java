package com.spingbootinit.context.UserContext;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserContextTest {

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void roleAndUsernameAreStoredIndependently() {
        UserContext.setCurrentRole("teacher");
        UserContext.setCurrentUsername("alice");

        assertEquals("teacher", UserContext.getCurrentRole());
        assertEquals("alice", UserContext.getCurrentUsername());
    }
}
