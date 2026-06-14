package com.rideswift.controller;

import com.rideswift.dto.request.SendMessageRequest;
import com.rideswift.dto.response.RideMessageResponse;
import com.rideswift.dto.response.RideParticipantsResponse;
import com.rideswift.security.SecurityUtils;
import com.rideswift.service.RideChatService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rides")
public class RideChatController {

    private final RideChatService chatService;

    public RideChatController(RideChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/{rideId}/participants")
    public ResponseEntity<RideParticipantsResponse> participants(@PathVariable UUID rideId) {
        return ResponseEntity.ok(chatService.participants(SecurityUtils.currentUserId(), rideId));
    }

    @GetMapping("/{rideId}/messages")
    public ResponseEntity<List<RideMessageResponse>> messages(@PathVariable UUID rideId) {
        return ResponseEntity.ok(chatService.messages(SecurityUtils.currentUserId(), rideId));
    }

    @PostMapping("/{rideId}/messages")
    public ResponseEntity<RideMessageResponse> send(
            @PathVariable UUID rideId, @Valid @RequestBody SendMessageRequest request) {
        RideMessageResponse response = chatService.send(SecurityUtils.currentUserId(), rideId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
