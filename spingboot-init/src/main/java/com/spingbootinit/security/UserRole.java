package com.spingbootinit.security;

/** Stable application roles. The persisted student value remains {@code user}. */
public enum UserRole {
    STUDENT("user"),
    TEACHER("teacher"),
    ADMIN("admin"),
    UNKNOWN("");

    private final String storageValue;

    UserRole(String storageValue) {
        this.storageValue = storageValue;
    }

    public String storageValue() {
        return storageValue;
    }
}
