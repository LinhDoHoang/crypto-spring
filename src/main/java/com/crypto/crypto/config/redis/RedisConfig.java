package com.crypto.crypto.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisConfig {

    @Bean
    RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();

        StringRedisSerializer strings = new StringRedisSerializer();
        RedisSerializer<Object> json = RedisSerializer.json();

        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(strings);
        template.setValueSerializer(json);
        template.setHashKeySerializer(strings);
        template.setHashValueSerializer(json);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    Map<String, ChannelTopic> topics() {
        Map<String, ChannelTopic> result = new HashMap<>();
        for (RedisChannels redisChannel : RedisChannels.values()) {
            ChannelTopic channel = new ChannelTopic(redisChannel.getChannelName());
            result.put(redisChannel.getChannelName(), channel);
        }

        return result;
    }

    @Bean
    RedisMessageListenerContainer redisContainer(RedisConnectionFactory redisConnectionFactory, MessageListenerAdapter messageListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(redisConnectionFactory);

        for (ChannelTopic channel : this.topics().values()) {
            container.addMessageListener(messageListenerAdapter, channel);
        }

        return container;
    }

    @Bean
    MessageListenerAdapter listenerAdapter(MessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber, "onMessage");
    }
}
