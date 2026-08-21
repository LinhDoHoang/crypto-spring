package com.crypto.crypto.config.redis;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RedisMessageDispatcher implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisMessageDispatcher.class);

    private final Map<String, RedisMessageHandler> handlersByChannel;

    public RedisMessageDispatcher(List<RedisMessageHandler> handlers) {
        Map<String, RedisMessageHandler> map = new HashMap<>();
        for (RedisMessageHandler h : handlers) {
            RedisMessageHandler prev = map.put(h.channel(), h);
            if (prev != null) {
                throw new IllegalStateException(
                    "Duplicate RedisMessageHandler for channel=" + h.channel()
                        + " (" + prev.getClass().getName() + " vs " + h.getClass().getName() + ")");
            }
        }
        this.handlersByChannel = Map.copyOf(map);
        log.info("Registered Redis handlers: {}", this.handlersByChannel.keySet());
    }

    @Override
    public void onMessage(Message message, byte @Nullable [] pattern) {
        String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);

        RedisMessageHandler handler = handlersByChannel.get(channel);
        if (handler == null) {
            log.warn("No handler registered for channel={}", channel);
            return;
        }

        try {
            handler.handle(payload);
        } catch (Exception ex) {
            log.error("Handler failed for channel={} payload={}", channel, payload, ex);
        }
    }
}
