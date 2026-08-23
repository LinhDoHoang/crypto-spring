package com.crypto.crypto.config.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                throw new MessagingException("Missing Authorization header on STOMP CONNECT");
            }

            String token = auth.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(token);
                String subject = jwt.getSubject();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                subject,
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        );
                accessor.setUser(authentication);
            } catch (Exception e) {
                throw new MessagingException("Invalid JWT on STOMP CONNECT: " + e.getMessage());
            }
        }

        return message;
    }
}
