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
@RequestMapping("/api/announce/edit")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class EditAnnounceController {

    // EditAnnounceService 타입으로 변경
    private final EditAnnounceService service;

    /** 공지사항 등록 - 관리자만 가능 */
    @PostMapping("")
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

    /** 공지사항 수정 - 관리자만 가능 */
    @PutMapping("/{announceNo}")
    public ResponseEntity<?> updateAnnounce(
            @PathVariable(name = "announceNo") int announceNo,
            @RequestBody Announce announce,
            HttpServletRequest request) {
        try {
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
        } catch (Exception e) {
            log.error("📛 공지사항 수정 실패", e);
            return ResponseEntity.status(500).body("서버 오류 발생");
        }
    }

    /** 공지사항 삭제 - 관리자만 가능 */
    @DeleteMapping("/{announceNo}")
    public ResponseEntity<?> deleteAnnounce(
            @PathVariable(name = "announceNo") int announceNo,
            HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(false);
            Member loginMember = session != null ? (Member) session.getAttribute("loginMember") : null;

            if (loginMember == null) {
                return ResponseEntity.status(401).body("로그인이 필요합니다.");
            }
            if (loginMember.getMemberAuth() != 0) {
                return ResponseEntity.status(403).body("관리자만 삭제할 수 있습니다.");
            }

            int result = service.deleteAnnounce(announceNo);
            if (result > 0) {
                return ResponseEntity.ok(Map.of("message", "공지사항 삭제 성공"));
            }
            return ResponseEntity.status(500).body("공지사항 삭제 실패");
        } catch (Exception e) {
            log.error("📛 공지사항 삭제 실패", e);
            return ResponseEntity.status(500).body("서버 오류 발생");
        }
    }
}
