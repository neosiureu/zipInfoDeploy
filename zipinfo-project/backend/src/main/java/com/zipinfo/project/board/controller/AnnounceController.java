package com.zipinfo.project.board.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.board.model.dto.Board;
import com.zipinfo.project.board.model.service.AnnounceService;
import com.zipinfo.project.member.model.dto.Member;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/board/announce")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")
public class AnnounceController {

    private final AnnounceService service;

    /**
     * 공지사항 목록 조회 (검색 포함)
     */
    @GetMapping("")
    public ResponseEntity<List<Board>> selectBoardList(
            @RequestParam(value = "cp", defaultValue = "1") int cp,
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "query", required = false) String query) {

        List<Board> resultList;

        if (key == null || key.trim().isEmpty()) {
            resultList = service.selectBoardList(cp);
        } else {
            resultList = service.searchList(key, query, cp);
        }


        return ResponseEntity.ok(resultList);
    }

    /**
     * 공지사항 상세 조회 + 조회수 증가 (쿠키 중복 방지)
     */
    @GetMapping("/{boardNo}")
    public ResponseEntity<?> selectAnnounceDetail(
            @PathVariable int boardNo,
            @SessionAttribute(value = "loginMember", required = false) Member loginMember,
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
                readCookie = new Cookie("readAnnounceId", "[" + boardNo + "]");
                shouldIncrease = true;
            } else if (!readCookie.getValue().contains("[" + boardNo + "]")) {
                readCookie.setValue(readCookie.getValue() + "[" + boardNo + "]");
                shouldIncrease = true;
            }

            if (shouldIncrease) {
                int result = service.increaseViewCount(boardNo);
                if (result > 0) {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime midnight = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                    long secondsUntilMidnight = Duration.between(now, midnight).getSeconds();

                    readCookie.setMaxAge((int) secondsUntilMidnight);
                    readCookie.setPath("/");
                    response.addCookie(readCookie);
                }
            }

            Board board = service.selectOne(Map.of("boardNo", boardNo));
            if (board == null) {
                return ResponseEntity.status(404).body("해당 게시글이 존재하지 않습니다.");
            }

            return ResponseEntity.ok(board);

        } catch (Exception e) {
            log.error("공지사항 상세 조회 실패", e);
            return ResponseEntity.status(500).body("서버 오류 발생");
        }
    }

    /**
     * 공지사항 등록 (관리자만 가능)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("")
    public ResponseEntity<?> createBoard(
            @RequestBody Board board,
            @SessionAttribute(value = "loginMember", required = false) Member loginMember) {

        if (loginMember == null) {
            return ResponseEntity.status(401).body("로그인이 필요합니다.");
        }

        board.setMemberNo(loginMember.getMemberNo());  // 작성자 설정

        int result = service.insertBoard(board);

        if (result > 0) {
            return ResponseEntity.ok(Map.of("message", "공지사항 등록 성공", "boardNo", board.getBoardNo()));
        }

        return ResponseEntity.status(500).body("공지사항 등록 실패");
    }


    /**
     * 공지사항 수정 (관리자만 가능)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{boardNo}")
    public ResponseEntity<?> updateBoard(
            @PathVariable int boardNo,
            @RequestBody Board board) {

        board.setBoardNo(boardNo);

        int result = service.updateBoard(board);

        if (result > 0) {
            return ResponseEntity.ok("공지사항 수정 성공");
        }

        return ResponseEntity.status(500).body("공지사항 수정 실패");
    }

    /**
     * 공지사항 삭제 (관리자만 가능)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{boardNo}")
    public ResponseEntity<?> deleteBoard(@PathVariable int boardNo) {

        int result = service.deleteBoard(Map.of("boardNo", boardNo));

        if (result > 0) {
            return ResponseEntity.ok("공지사항 삭제 성공");
        }

        return ResponseEntity.status(500).body("공지사항 삭제 실패");
    }
}
