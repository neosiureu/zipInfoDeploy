package com.zipinfo.project.webSocket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/announce")
    public void postAnnouncement() {
        // 예시: 공지 등록 로직 생략 후 바로 broadcast
        messagingTemplate.convertAndSend("/topic/notice", "새 공지가 등록되었습니다!");
    }

    // 클라이언트가 보낼 경우 사용
    @MessageMapping("/send")
    public void receiveFromClient(String message) {
        messagingTemplate.convertAndSend("/topic/notice", message);
    }
}