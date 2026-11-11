package com.yachiqo.spotgo.iam.application;

public interface PasswordResetNotificationService {
    void sendPasswordResetCode(String email, String code);

    default void sendAccountInvitationCode(String email, String code) {
        sendPasswordResetCode(email, code);
    }
}
