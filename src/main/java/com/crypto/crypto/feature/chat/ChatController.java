package com.crypto.crypto.feature.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/chat.send")
    @SendTo("/topic/messages")
    public ChatMessage broadcast(ChatMessage msg, Principal user) {
        String sender = user != null ? user.getName() : msg.from();
        return ChatMessage.of(sender, msg.text());
    }

    @MessageMapping("/chat.private")
    public void sendPrivate(PrivateChatRequest request, Principal sender) {
        simpMessagingTemplate.convertAndSendToUser(
                request.userId(),
                "/queue/messages",
                PrivateChatRequest.of(sender.getName(), request.text())
        );
    }
}
