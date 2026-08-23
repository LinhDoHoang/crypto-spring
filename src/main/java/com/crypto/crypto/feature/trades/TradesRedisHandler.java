package com.crypto.crypto.feature.trades;

import com.crypto.crypto.config.redis.RedisMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class TradesRedisHandler implements RedisMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TradesRedisHandler.class);

    private final SimpMessagingTemplate messagingTemplate;

    public TradesRedisHandler(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public String channel() {
        return "trades";
    }

    @Override
    public void handle(String jsonPayload) {
        log.debug("Received trade: {}", jsonPayload);
        messagingTemplate.convertAndSend("/topic/trades", jsonPayload);
    }
}
