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
@RequestMapping("/api/board")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:5173")  // Vite 개발서버 주소(프론트 주소)
public class AnnounceController {

    private final AnnounceService service;

    /**
     * 게시글 목록 조회 (검색 포함)
     */
    @GetMapping("/{boardSubject}")
    public ResponseEntity<List<Board>> selectBoardList(
            @PathVariable("boardSubject") String boardSubject,
            @RequestParam(value = "cp", defaultValue = "1") int cp,
            @RequestParam(value = "key", required = false) String key,
            @RequestParam(value = "query", required = false) String query) {

        List<Board> resultList;

        if (key == null || key.isBlank()) {
            resultList = service.selectBoardList(boardSubject, cp);
        } else {
            resultList = service.searchList(boardSubject, key, query, cp);
        }

        return ResponseEntity.ok(resultList);
    }

    /**
     * 게시글 상세 조회 + 조회수 증가 (쿠키 기반 중복 방지)
     */
    @GetMapping("/{boardSubject}/{boardNo}")
    public ResponseEntity<?> selectAnnounceDetail(
            @PathVariable("boardSubject") String boardSubject,
            @PathVariable("boardNo") int boardNo,
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
     * 게시글 등록 (관리자만 가능)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{boardSubject}")
    public ResponseEntity<?> createBoard(
            @PathVariable String boardSubject,
            @RequestBody Board board) {

        board.setBoardSubject(boardSubject);
        int result = service.insertBoard(board);

        if (result > 0) {
            return ResponseEntity.ok(Map.of("message", "게시글 등록 성공", "boardNo", board.getBoardNo()));
        }

        return ResponseEntity.status(500).body("게시글 등록 실패");
    }

    /**
     * 게시글 수정 (관리자만 가능)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{boardSubject}/{boardNo}")
    public ResponseEntity<?> updateBoard(
            @PathVariable String boardSubject,
            @PathVariable int boardNo,
            @RequestBody Board board) {

        board.setBoardSubject(boardSubject);
        board.setBoardNo(boardNo);
        int result = service.updateBoard(board);

        if (result > 0) {
            return ResponseEntity.ok("게시글 수정 성공");
        }

        return ResponseEntity.status(500).body("게시글 수정 실패");
    }

    /**
     * 게시글 삭제 (관리자만 가능)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{boardSubject}/{boardNo}")
    public ResponseEntity<?> deleteBoard(
            @PathVariable String boardSubject,
            @PathVariable int boardNo) {

        Map<String, Object> param = Map.of("boardSubject", boardSubject, "boardNo", boardNo);
        int result = service.deleteBoard(param);

        if (result > 0) {
            return ResponseEntity.ok("게시글 삭제 성공");
        }

        return ResponseEntity.status(500).body("게시글 삭제 실패");
    }
}
