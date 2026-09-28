package com.yachiqo.spotgo.iam.application;

public interface PasswordResetNotificationService {
    void sendPasswordResetCode(String email, String code);
}
