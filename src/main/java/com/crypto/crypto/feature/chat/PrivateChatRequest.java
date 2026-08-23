package com.crypto.crypto.feature.chat;

import java.time.Instant;

public record PrivateChatRequest(String userId, String text, Instant sentAt) {
    public static PrivateChatRequest of(String userId,  String text) {
        return new PrivateChatRequest(userId, text, Instant.now());
    }
}
