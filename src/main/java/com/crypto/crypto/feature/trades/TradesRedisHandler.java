package com.crypto.crypto.feature.trades;

import com.crypto.crypto.config.redis.RedisMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class TradesRedisHandler implements RedisMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(TradesRedisHandler.class);

    @Override
    public String channel() {
        return "trades";
    }

    @Override
    public void handle(String jsonPayload) {
        log.info("Received trade: {}", jsonPayload);
    }
}
