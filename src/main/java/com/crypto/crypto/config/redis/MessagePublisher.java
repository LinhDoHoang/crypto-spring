package com.crypto.crypto.config.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class MessagePublisher {
    private final StringRedisTemplate redisTemplate;

    MessagePublisher(StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = stringRedisTemplate;
    }

    public void publish(String channelName, String jsonMessage) {
        this.redisTemplate.convertAndSend(channelName, jsonMessage);
    }
}
