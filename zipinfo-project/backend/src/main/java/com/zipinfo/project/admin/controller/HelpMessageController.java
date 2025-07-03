package com.zipinfo.project.admin.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import com.zipinfo.project.admin.model.service.HelpMessageService;
import com.zipinfo.project.member.model.dto.Member;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@CrossOrigin(origins = "http://localhost:3000", allowCredentials = "true")
@RestController
@RequestMapping("/api/help")
@RequiredArgsConstructor
public class HelpMessageController {

    private final HelpMessageService helpMessageService;

    @Value("${my.message.folder-path}")
    private String messageFolderPath;

    /** 문의 리스트
     * @return
     */
    @GetMapping("/list")
    public ResponseEntity<List<HelpMessage>> getAllMessages() {
        return ResponseEntity.ok(helpMessageService.getAllMessages());
    }

    /** 문의 답변 조회
     * @param messageNo
     * @return
     */
    @GetMapping("/reply")
    public ResponseEntity<?> getInquiryReply(@RequestParam("messageNo") int messageNo) {
        try {
            HelpMessage reply = helpMessageService.getHelpMessageById(messageNo);
            if (reply == null) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(reply);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("서버 오류 발생: " + e.getMessage());
        }
    }


    /** 답변 등록
     * @param session
     * @param message
     * @return
     */
    @PostMapping("/reply")
    public ResponseEntity<?> postReply(@AuthenticationPrincipal Member loginMember,
                                       @RequestBody HelpMessage message) {
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        message.setSenderNo(loginMember.getMemberNo());
        boolean success = helpMessageService.saveReply(message);

        return success
            ? ResponseEntity.ok("답변 등록 성공")
            : ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("답변 등록 실패");
    }
    
    /** 수정
     * @param helpMessage
     * @return
     */
    @PutMapping("/reply/update")
    public ResponseEntity<?> updateReply(@RequestBody HelpMessage helpMessage) {
        int result = helpMessageService.updateReply(helpMessage);
        if (result > 0) {
            return ResponseEntity.ok("답변 수정 완료");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("답변 수정 실패");
        }
    }
    
    /** 답변 수정 시 원글 + 답변 함께 조회 */
    @PostMapping("/reply/view")
    public ResponseEntity<Map<String, HelpMessage>> getOriginalAndReply(@RequestBody Map<String, Integer> req) {
        Integer replyMessageNoObj = req.get("messageNo");
        if (replyMessageNoObj == null) {
            return ResponseEntity.badRequest().body(null); // 400 Bad Request 반환
        }
        int replyMessageNo = replyMessageNoObj;

        HelpMessage reply = helpMessageService.getHelpMessageById(replyMessageNo);
        HelpMessage original = helpMessageService.getOriginalByReplyMessageNo(replyMessageNo);

        if (reply == null || original == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        Map<String, HelpMessage> result = new HashMap<>();
        result.put("original", original);
        result.put("reply", reply);

        return ResponseEntity.ok(result);
    }



    @PatchMapping("/message/read/{messageNo}")
    public ResponseEntity<?> updateReadFlag(@PathVariable("messageNo") int messageNo) {
        boolean result = helpMessageService.updateReadFlag(messageNo);
        return result ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    /** 미답변
     * @param adminId
     * @return
     */
    @GetMapping("/unanswered")
    public List<HelpMessage> getUnanswered(@RequestParam("adminId") int adminId) {
        return helpMessageService.getUnansweredMessages(adminId);
    }

    /** 답변하면 y
     * @param userNo
     * @return
     */
    @GetMapping("/answered")
    public List<HelpMessage> getAnsweredMessages(@RequestParam(name = "userNo") int userNo) {
        return helpMessageService.getAnsweredMessagesByUser(userNo);
    }


    /** 파일 다운로드
     * @param filename
     * @return
     */
    @GetMapping("/message/messageFile/{filename:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("filename") String filename) {
        try {
            Path filePath = Paths.get(messageFolderPath).resolve(filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
