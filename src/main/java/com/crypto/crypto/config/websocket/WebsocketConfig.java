package com.crypto.crypto.config.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebsocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthInterceptor stompAuthInterceptor;
    private final String rabbitUser;
    private final String rabbitPassword;
    private final String rabbitHost;
    private final int rabbitPort;

    public WebsocketConfig(
            StompAuthInterceptor stompAuthInterceptor,
            @Value("${RABBITMQ_DEFAULT_USER}") String rabbitUser,
            @Value("${RABBITMQ_DEFAULT_PASSWORD}") String rabbitPassword,
            @Value("${RABBITMQ_HOST:localhost}") String rabbitHost,
            @Value("${RABBITMQ_PORT:61613}") int rabbitPort
    ) {
        this.stompAuthInterceptor = stompAuthInterceptor;
        this.rabbitUser = rabbitUser;
        this.rabbitPassword = rabbitPassword;
        this.rabbitHost = rabbitHost;
        this.rabbitPort = rabbitPort;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(rabbitHost)
                .setRelayPort(rabbitPort)
                .setVirtualHost("/")
                .setClientLogin(rabbitUser)
                .setClientPasscode(rabbitPassword)
                .setSystemLogin(rabbitUser)
                .setSystemPasscode(rabbitPassword)
                .setUserDestinationBroadcast("/topic/unresolved-user")
                .setUserRegistryBroadcast("/topic/simp-user-registry");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor);
    }
}
