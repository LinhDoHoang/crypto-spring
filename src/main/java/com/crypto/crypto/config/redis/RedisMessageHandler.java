package com.crypto.crypto.config.redis;

public interface RedisMessageHandler {
    String channel();

    void handle(String jsonPayload);
}
