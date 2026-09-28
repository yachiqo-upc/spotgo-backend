package com.yachiqo.spotgo.iam.application;

final class PasswordPolicy {

    private PasswordPolicy() {
    }

    static void validate(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must contain at least 8 characters");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*\\d.*")) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }
        if (!password.matches(".*[^A-Za-z0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one symbol");
        }
    }
}
