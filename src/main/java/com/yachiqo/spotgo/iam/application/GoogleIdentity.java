package com.yachiqo.spotgo.iam.application;

public record GoogleIdentity(String subject, String email) {
    public GoogleIdentity {
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("A verified Google subject and email are required.");
        }
    }
}
