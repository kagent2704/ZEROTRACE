package com.zerotrace.auth_server.controller;

import com.zerotrace.auth_server.model.MessagePacket;
import com.zerotrace.auth_server.model.MessageRequest;
import com.zerotrace.auth_server.model.SendMessageResponse;
import com.zerotrace.auth_server.service.MessageRelayService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/message")
@CrossOrigin(origins = "*")
public class MessageController {

    private final MessageRelayService messageRelayService;

    public MessageController(MessageRelayService messageRelayService) {
        this.messageRelayService = messageRelayService;
    }

    @GetMapping("/ping")
    public String test() {
        return "Message relay working";
    }

    @PostMapping("/send")
    public SendMessageResponse sendMessage(
            Authentication authentication,
            @RequestBody MessageRequest request,
            HttpServletRequest httpRequest
    ) {
        return messageRelayService.relay(authentication.getName(), request, httpRequest);
    }

    @GetMapping("/inbox/{username}")
    public List<MessagePacket> inbox(Authentication authentication, @PathVariable String username) {
        return messageRelayService.fetchInbox(authentication.getName(), username);
    }
}
