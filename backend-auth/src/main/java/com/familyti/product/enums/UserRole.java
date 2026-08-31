package com.familyti.product.enums;

public enum UserRole {

    ADMIN,
    USER;

    public String authority() {
        return "ROLE_" + name();
    }
}