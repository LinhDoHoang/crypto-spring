package com.crypto.crypto.config.redis;

import lombok.Getter;

@Getter
public enum RedisChannels {
    TRADES("trades");

    private final String channelName;

    RedisChannels(String channelName) {
        this.channelName = channelName;
    }
}
