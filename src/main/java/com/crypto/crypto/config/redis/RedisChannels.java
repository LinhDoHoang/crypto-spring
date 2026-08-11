package com.crypto.crypto.config.redis;

public enum RedisChannels {
    TRADES("trades");

    private final String channelName;

    RedisChannels(String channelName) {
        this.channelName = channelName;
    }

    public String getChannelName() {
        return this.channelName;
    }
}
