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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import com.zipinfo.project.admin.model.service.HelpMessageService;
import com.zipinfo.project.member.model.dto.Member;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/help")
@RequiredArgsConstructor
public class HelpMessageController {

    private final HelpMessageService helpMessageService;

    @Value("${my.message.folder-path}")
    private String messageFolderPath;

    @GetMapping("/list")
    public ResponseEntity<List<HelpMessage>> getAllMessages() {
        return ResponseEntity.ok(helpMessageService.getAllMessages());
    }

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

    




    @PostMapping("/reply")
    public ResponseEntity<?> postReply(HttpSession session, @RequestBody HelpMessage message) {
        Member loginMember = (Member) session.getAttribute("loginMember");
        message.setSenderNo(loginMember.getMemberNo());

        boolean success = helpMessageService.saveReply(message);
        if (!success) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("답변 등록 실패");
        }
        return ResponseEntity.ok("답변 등록 성공");
    }

    @PatchMapping("/message/read/{messageNo}")
    public ResponseEntity<?> updateReadFlag(@PathVariable("messageNo") int messageNo) {
        boolean result = helpMessageService.updateReadFlag(messageNo);
        return result ? ResponseEntity.ok().build() : ResponseEntity.badRequest().build();
    }

    @GetMapping("/unanswered")
    public List<HelpMessage> getUnanswered(@RequestParam("adminId") int adminId) {
        return helpMessageService.getUnansweredMessages(adminId);
    }

    @GetMapping("/answered")
    public List<HelpMessage> getAnsweredMessages(@RequestParam(name = "userNo") int userNo) {
        return helpMessageService.getAnsweredMessagesByUser(userNo);
    }


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
