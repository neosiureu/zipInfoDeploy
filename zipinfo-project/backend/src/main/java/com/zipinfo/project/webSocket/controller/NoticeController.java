package com.zipinfo.project.webSocket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.member.model.dto.Member;


@RestController
@RequiredArgsConstructor
public class NoticeController {

    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("/announce")
    public void announceToast() {
        // 예시: 공지 등록 로직 생략 후 바로 broadcast
        messagingTemplate.convertAndSend("/topic/notice", "새 공지가 등록되었습니다!");
    }
    
    @PostMapping("/neighbor/insert")
    public void boardToast(@AuthenticationPrincipal Member loginMember,
                           @RequestBody Member board) {
  System.out.println("=== NoticeController 디버깅 ===");
    System.out.println("loginMember: " + loginMember);
    System.out.println("loginMember null여부: " + (loginMember == null));
    if (loginMember != null) {
        System.out.println("loginMember.getMemberLocation(): " + loginMember.getMemberLocation());
        System.out.println("loginMember.getMemberLogin(): " + loginMember.getMemberLogin());
    }
    System.out.println("board.getMemberLocation(): " + board.getMemberLocation());
    System.out.println("===============================");
        int memberLocation = loginMember.getMemberLocation();      //  예전 그대로
        int boardLocation  = board.getMemberLocation();
        int sliceLocation  = boardLocation / 1000;

        if (String.valueOf(memberLocation).length() == 5) {
            messagingTemplate.convertAndSend(
                "/topic/region/" + boardLocation,
                "우리동네 게시판에서 확인하세요!"
            );
        } else {
            messagingTemplate.convertAndSend(
                "/topic/region/" + sliceLocation,
                "우리동네 게시판에서 확인하세요!"
            );
        }
    }

    // 클라이언트가 보낼 경우 사용
    @MessageMapping("/send")
    public void receiveFromClient(String message) {
        messagingTemplate.convertAndSend("/topic/notice", message);
    }
}