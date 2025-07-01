package com.zipinfo.project.admin.controller;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.core.io.UrlResource;
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

import java.net.URLEncoder;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;


@RestController
@RequestMapping("/api/help")
@RequiredArgsConstructor
public class HelpMessageController {

    // HelpMessageService 주입
    private final HelpMessageService helpMessageService;
    
    // application.properties (config.properties)에서 문의 파일 저장 폴더 경로 주입
    @Value("${my.message.folder-path}")
    private String messageFolderPath;

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
        try {
            HelpMessage msg = helpMessageService.getHelpMessageById(messageNo);
            if (msg == null) {
                return ResponseEntity.notFound().build();
            }

            // DB에 파일명 없으면, 예를 들어 메시지 번호로 파일명 규칙 만들어서 세팅(예시)
            // 실제로는 파일명 관리 로직에 따라 다름
            String assumedFileRename = "message_" + messageNo + ".dat";  // 예: message_17.dat 등
            msg.setFileUrl("/api/help/file/" + assumedFileRename);

            return ResponseEntity.ok(msg);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류 발생: " + e.getMessage());
        }
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
    public ResponseEntity<?> updateReadFlag(@PathVariable("messageNo") int messageNo) {
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

    
    /** 파일 다운로드
     * @param messageNo
     * @return
     */
    @GetMapping("/file/{fileName:.+}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get(messageFolderPath).resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String encodedFilename = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}


    

