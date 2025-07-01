package com.zipinfo.project.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import com.zipinfo.project.admin.model.service.HelpMessageService;
import com.zipinfo.project.member.model.dto.Member;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/help")
@RequiredArgsConstructor
public class HelpMessageController {

    // HelpMessageService 주입
    private final HelpMessageService helpMessageService;

    /**
     * 전체 문의 목록 조회 API
     * GET /api/help/list
     * @return 문의글 리스트를 JSON 형태로 반환
     */
    @GetMapping("/list")
    public ResponseEntity<List<HelpMessage>> getAllMessages() {
        return ResponseEntity.ok(helpMessageService.getAllMessages());
    }

    /**
     * 특정 문의글 상세 조회 API
     * GET /api/help/detail/{messageNo}
     * @param messageNo 조회할 문의글 번호 (PathVariable)
     * @return 해당 문의글 정보 또는 404 Not Found 반환
     */
    @GetMapping("/detail")
    public ResponseEntity<Object> getHelpMessageDetail(@RequestParam("messageNo") int messageNo) {
        HelpMessage msg = helpMessageService.getHelpMessageById(messageNo);
        if (msg == null) {
            // 문의글이 없으면 404 반환
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(msg);
    }

    /**
     * 문의글에 답변 등록 API
     * POST /api/help/reply/{messageNo}
     * @param messageNo 답변할 문의글 번호 (PathVariable)
     * @param replyDto 요청 본문에 담긴 답변 내용 DTO (JSON)
     * @return 성공 시 200 OK, 실패 시 500 Internal Server Error와 메시지 반환
     */
    @PostMapping("/reply")
    public ResponseEntity<?> postReply(HttpSession session, @RequestBody HelpMessage message) {
    	Member loginMember = (Member)session.getAttribute("loginMember");
    	
    	message.setSenderNo(loginMember.getMemberNo());
    	
        boolean success = helpMessageService.saveReply(message);
        if (!success) {
            // 답변 등록 실패 시 500 에러 반환
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("답변 등록 실패");
        }
        // 답변 등록 성공 시 OK 메시지 반환
        return ResponseEntity.ok("답변 등록 성공");
    }
    
    /** 관리자가 문의내용 확인했을 경우 DB에 해당 문의글 READ_FL 'Y'로 변경
     * @param messageNo
     * @return
     */
    @PatchMapping("/message/read/{messageNo}")
    public ResponseEntity<?> updateReadFlag(@PathVariable int messageNo) {
        boolean result = helpMessageService.updateReadFlag(messageNo);
        return result ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/unanswered")
    public List<HelpMessage> getUnanswered(@RequestParam("adminId") int adminId) {
        return helpMessageService.getUnansweredMessages(adminId);
    }

    @GetMapping("/answered")
    public List<HelpMessage> getAnswered(@RequestParam("userNo") int userNo) {
        return helpMessageService.getAnsweredMessagesByUser(userNo);
    }

}
