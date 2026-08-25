package com.crypto.crypto.config.websocket;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class HelloWebSocketController {

    @MessageMapping("/hello")
    @SendTo("/topic/hello")
    public HelloResponse hello(HelloRequest request) {
        String message = request == null || request.message() == null
                ? "Hello from Spring Boot"
                : request.message();
        return new HelloResponse("Hello from Spring Boot: " + message);
    }

    public record HelloRequest(String message) {
    }

    public record HelloResponse(String message) {
    }
}
