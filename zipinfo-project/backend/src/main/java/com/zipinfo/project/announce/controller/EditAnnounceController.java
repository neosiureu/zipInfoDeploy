package com.zipinfo.project.announce.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.service.EditAnnounceService;
import com.zipinfo.project.member.model.dto.Member;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/announce")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class EditAnnounceController {

    private final EditAnnounceService service;

    /** 공지사항 등록 - 관리자만 가능 */
    @PostMapping("/write")
    public ResponseEntity<?> createAnnounce(@RequestBody Announce announce, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).body("세션 없음 - 로그인 필요");
        }

        Member loginMember = (Member) session.getAttribute("loginMember");
        if (loginMember == null) {
            return ResponseEntity.status(401).body("로그인 필요");
        }
        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity.status(403).body("관리자만 공지사항을 등록할 수 있습니다.");
        }

        announce.setMemberNo(loginMember.getMemberNo());
        int result = service.insertAnnounce(announce);

        if (result > 0) {
            return ResponseEntity.ok(Map.of("message", "공지사항 등록 성공", "announceNo", announce.getAnnounceNo()));
        }
        return ResponseEntity.status(500).body("공지사항 등록 실패");
    }
    
    
    /** 기존 내용 가져오기
     * @param announceNo
     * @return
     */
    @GetMapping("/detail/{announceNo}")
    public ResponseEntity<?> getAnnounceDetail(@PathVariable int announceNo)  {
        log.info("공지사항 상세 조회 요청 - announceNo: {}", announceNo);
        Announce announce = service.selectAnnounceByNo(announceNo);
        if (announce != null) {
            return ResponseEntity.ok(announce);
        } else {
            return ResponseEntity.status(404).body("공지사항을 찾을 수 없습니다.");
        }
    }


    /** 공지사항 수정 - 관리자만 가능 */
    @PutMapping("/edit/{announceNo}")
    public ResponseEntity<?> updateAnnounce(
            @PathVariable("announceNo") int announceNo,  // 변수명 명시 필수!
            @RequestBody Announce announce,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        Member loginMember = session != null ? (Member) session.getAttribute("loginMember") : null;

        if (loginMember == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity.status(403).body("관리자만 수정할 수 있습니다.");
        }

        announce.setAnnounceNo(announceNo);
        announce.setMemberNo(loginMember.getMemberNo());

        int result = service.updateAnnounce(announce);
        if (result > 0) {
            return ResponseEntity.ok(Map.of("message", "공지사항 수정 성공"));
        }
        return ResponseEntity.status(500).body("공지사항 수정 실패");
    }



    /** 공지사항 삭제 - 관리자만 가능 */
    @PostMapping("/detail/delete")
    public ResponseEntity<?> deleteAnnounce(@RequestBody Announce announce, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        log.info("Session: {}", session);

        Member loginMember = session != null ? (Member) session.getAttribute("loginMember") : null;
        log.info("Login member: {}", loginMember);
        
        int announceNo = announce.getAnnounceNo();

        if (loginMember == null) {
            log.warn("No login member - 401");
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        log.info("Member Auth: {}", loginMember.getMemberAuth());
        if (loginMember.getMemberAuth() != 0) {
            log.warn("Insufficient auth - {}", loginMember.getMemberAuth());
            return ResponseEntity.status(403).body("관리자만 삭제할 수 있습니다.");
        }

        int result = service.deleteAnnounce(announceNo);
        if (result > 0) {
            log.info("삭제 성공: {}", announceNo);
            return ResponseEntity.ok(Map.of("message", "공지사항 삭제 성공"));
        }
        log.error("삭제 실패: {}", announceNo);
        return ResponseEntity.status(500).body("공지사항 삭제 실패");
    }


}
