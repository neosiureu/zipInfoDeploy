package com.zipinfo.project.announce.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.service.AnnounceService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/announce")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {
	    "http://localhost:5173",
	    "http://zipinfo.site",
	    "https://zipinfo.site", 
	    "http://www.zipinfo.site",
	    "https://www.zipinfo.site"
	})public class AnnounceController {

    private final AnnounceService service;

    /** 공지사항 목록 조회 - 페이징 및 검색 키워드 처리 */
    @GetMapping("")
    public ResponseEntity<Map<String, Object>> selectAnnounceList(
            @RequestParam(value = "cp", defaultValue = "1") int cp,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "query", required = false) String query) {

        List<Announce> list;
        int totalCount;

        if (key == null || key.trim().isEmpty()) {
            list = service.selectAnnounceList(cp, size);
            totalCount = service.countAnnounce();
        } else {
            list = service.searchList(key, query, cp, size);
            totalCount = service.countSearchAnnounce(key, query);
        }

        int totalPages = (int) Math.ceil((double) totalCount / size);

        Map<String, Object> result = Map.of(
                "posts", list,
                "totalPages", totalPages
        );

        return ResponseEntity.ok(result);
    }

    /** 공지사항 상세 조회 - 조회수 중복 증가 방지 쿠키 처리 */
    @GetMapping("/{announceNo}")
    public ResponseEntity<?> selectAnnounceDetail(@PathVariable(name = "announceNo") int announceNo,
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

            Announce announce = service.selectOne(announceNo);
            if (announce == null) {
                return ResponseEntity.status(404).body("해당 게시글이 존재하지 않습니다.");
            }

            return ResponseEntity.ok(announce);

        } catch (Exception e) {
            log.error("📛 공지사항 상세 조회 실패", e);
            return ResponseEntity.status(500).body("서버 오류 발생");
        }
    }
}
