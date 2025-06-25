package com.zipinfo.project.announce.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final MemberMapper memberMapper; // 이메일로 회원번호 조회용

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

    /** ✅ 공지사항 등록 (Authentication으로 권한 검사) */
    @PostMapping("")
    public ResponseEntity<?> createBoard(@RequestBody Announce announce) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        // 권한 검사 (ADMIN 여부 확인)
        boolean isAdmin = authentication.getAuthorities().stream()
            .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            return ResponseEntity.status(403).body("관리자 권한이 필요합니다.");
        }

        // 로그인한 유저 정보 가져오기
        String email = authentication.getName();
        Member loginMember = memberMapper.selectByEmail(email);
        if (loginMember == null) {
            return ResponseEntity.status(401).body("사용자 정보를 찾을 수 없습니다.");
        }

        announce.setMemberNo(loginMember.getMemberNo());

        int result = service.insertAnnounce(announce);
        if (result > 0) {
            return ResponseEntity.ok(Map.of("message", "공지사항 등록 성공", "announceNo", announce.getAnnounceNo()));
        }
        return ResponseEntity.status(500).body("공지사항 등록 실패");
    }

    // 수정/삭제 메서드는 동일한 방식으로 바꾸면 됩니다.
}
