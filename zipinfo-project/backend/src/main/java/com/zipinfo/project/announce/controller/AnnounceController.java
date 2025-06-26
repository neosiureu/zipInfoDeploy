package com.zipinfo.project.announce.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.service.AnnounceService;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.mapper.MemberMapper;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/board/announce")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AnnounceController {

    private final AnnounceService service;
    private final MemberMapper memberMapper;

    /** 공지사항 목록 조회 */
    @GetMapping("")
    public ResponseEntity<List<Announce>> selectBoardList(
            @RequestParam(value = "cp", defaultValue = "1") int cp,
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "query", required = false) String query) {

        List<Announce> resultList = (key == null || key.trim().isEmpty())
            ? service.selectAnnounceList(cp)
            : service.searchList(key, query, cp);

        return ResponseEntity.ok(resultList);
    }

    /** 공지사항 상세 조회 */
    @GetMapping("/{announceNo}")
    public ResponseEntity<?> selectAnnounceDetail(
            @PathVariable int announceNo,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            Cookie[] cookies = request.getCookies();
            Cookie readCookie = null;

            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("readAnnounceId".equals(cookie.getName())) {
                        readCookie = cookie;
                        break;
                    }
                }
            }

            boolean shouldIncrease = false;

            if (readCookie == null) {
                readCookie = new Cookie("readAnnounceId", "[" + announceNo + "]");
                shouldIncrease = true;
            } else if (!readCookie.getValue().contains("[" + announceNo + "]")) {
                readCookie.setValue(readCookie.getValue() + "[" + announceNo + "]");
                shouldIncrease = true;
            }

            if (shouldIncrease) {
                int result = service.increaseViewCount(announceNo);
                if (result > 0) {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime midnight = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    long secondsUntilMidnight = Duration.between(now, midnight).getSeconds();

                    readCookie.setMaxAge((int) secondsUntilMidnight);
                    readCookie.setPath("/");
                    response.addCookie(readCookie);
                }
            }

            Announce announce = service.selectOne(Map.of("announceNo", announceNo));
            if (announce == null) {
                return ResponseEntity.status(404).body("해당 게시글이 존재하지 않습니다.");
            }

            return ResponseEntity.ok(announce);

        } catch (Exception e) {
            log.error("공지사항 상세 조회 실패", e);
            return ResponseEntity.status(500).body("서버 오류 발생");
        }
    }

    /** 공지사항 등록 (관리자만) */
    @PostMapping("")
    public ResponseEntity<?> createBoard(@RequestBody Announce announce, HttpServletRequest request) {
        Member loginMember = (Member) request.getSession().getAttribute("loginMember");
        log.info("Session loginMember: {}", loginMember);

        // 권한 체크 주석 처리
        /*
        if (loginMember == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity.status(403).body("관리자만 공지사항을 등록할 수 있습니다.");
        }
        */

        if (loginMember != null) {
            announce.setMemberNo(loginMember.getMemberNo());
        } else {
            // 로그인 정보가 없을 경우, 기본값 설정 가능 (예: 0 또는 -1)
            announce.setMemberNo(0);
        }

        int result = service.insertAnnounce(announce);
        if (result > 0) {
            return ResponseEntity.ok(Map.of("message", "공지사항 등록 성공", "announceNo", announce.getAnnounceNo()));
        }
        return ResponseEntity.status(500).body("공지사항 등록 실패");
    }

    /** 공지사항 수정 (관리자만) */
    @PutMapping("/{announceNo}")
    public ResponseEntity<?> updateBoard(@PathVariable int announceNo,
                                         @RequestBody Announce announce,
                                         HttpServletRequest request) {

        Member loginMember = (Member) request.getSession().getAttribute("loginMember");

        // 권한 체크 주석 처리
        /*
        if (loginMember == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity.status(403).body("관리자만 수정할 수 있습니다.");
        }
        */

        if (loginMember != null) {
            announce.setMemberNo(loginMember.getMemberNo());
        } else {
            announce.setMemberNo(0);
        }
        announce.setAnnounceNo(announceNo);

        int result = service.updateAnnounce(announce);
        if (result > 0) {
            return ResponseEntity.ok(Map.of("message", "공지사항 수정 성공"));
        }
        return ResponseEntity.status(500).body("공지사항 수정 실패");
    }

    /** 공지사항 삭제 (관리자만) */
    @DeleteMapping("/{announceNo}")
    public ResponseEntity<?> deleteBoard(@PathVariable int announceNo,
                                         HttpServletRequest request) {

        Member loginMember = (Member) request.getSession().getAttribute("loginMember");

        // 권한 체크 주석 처리
        /*
        if (loginMember == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }
        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity.status(403).body("관리자만 삭제할 수 있습니다.");
        }
        */

        int result = service.deleteAnnounce(announceNo);
        if (result > 0) {
            return ResponseEntity.ok(Map.of("message", "공지사항 삭제 성공"));
        }
        return ResponseEntity.status(500).body("공지사항 삭제 실패");
    }
}
