package com.crypto.crypto.feature.chat;

import java.time.Instant;

public record ChatMessage(String from, String text, Instant sentAt) {

    public static ChatMessage of(String from, String text) {
        return new ChatMessage(from, text, Instant.now());
    }
}
