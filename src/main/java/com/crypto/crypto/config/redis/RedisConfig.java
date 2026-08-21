package com.crypto.crypto.config.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

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

    @Bean(destroyMethod = "stop")
    RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory factory,
            RedisMessageDispatcher dispatcher) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        for (RedisChannels ch : RedisChannels.values()) {
            container.addMessageListener(dispatcher, new ChannelTopic(ch.getChannelName()));
        }
        container.setTaskExecutor(redisSubscriberExecutor());
        return container;
    }

    @Bean
    Executor redisSubscriberExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(16);
        exec.setQueueCapacity(500);
        exec.setThreadNamePrefix("redis-sub-");
        exec.initialize();
        return exec;
    }
}
