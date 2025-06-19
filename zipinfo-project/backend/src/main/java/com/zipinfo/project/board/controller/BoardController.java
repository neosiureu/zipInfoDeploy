package com.zipinfo.project.board.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.board.model.dto.Board;
import com.zipinfo.project.board.model.service.BoardService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/notice")
@Slf4j
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    // 1. 공지사항 목록 조회 (전체 사용자 접근 가능)
    @GetMapping
    public ResponseEntity<List<Board>> selectNoticeList() {
        try {
            List<Board> noticeList = boardService.selectNoticeList();
            return ResponseEntity.ok(noticeList);
        } catch (Exception e) {
            log.error("공지사항 목록 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 2. 공지사항 상세 조회 (전체 사용자 접근 가능)
    @GetMapping("/{boardNo}")
    public ResponseEntity<Board> selectNotice(@PathVariable int boardNo) {
        try {
            Board notice = boardService.selectNotice(boardNo);
            if (notice != null) {
                return ResponseEntity.ok(notice);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            log.error("공지사항 상세 조회 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // 3. 공지사항 작성 (관리자 권한 필요)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<String> insertNotice(@RequestBody Board notice) {
        try {
            int result = boardService.insertNotice(notice);
            if (result > 0) {
                return ResponseEntity.status(HttpStatus.CREATED).body("공지사항이 등록되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("공지사항 등록 실패");
            }
        } catch (Exception e) {
            log.error("공지사항 등록 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류 발생");
        }
    }

    // 4. 공지사항 수정 (관리자 권한 필요)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{boardNo}")
    public ResponseEntity<String> updateNotice(@PathVariable int boardNo, @RequestBody Board notice) {
        try {
            notice.setBoardNo(boardNo); // 경로 변수로 받은 번호를 DTO에 세팅
            int result = boardService.updateNotice(notice);
            if (result > 0) {
                return ResponseEntity.ok("공지사항이 수정되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("공지사항 수정 실패");
            }
        } catch (Exception e) {
            log.error("공지사항 수정 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류 발생");
        }
    }

    // 5. 공지사항 삭제 (관리자 권한 필요)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{boardNo}")
    public ResponseEntity<String> deleteNotice(@PathVariable int boardNo) {
        try {
            int result = boardService.deleteNotice(boardNo);
            if (result > 0) {
                return ResponseEntity.ok("공지사항이 삭제되었습니다.");
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("공지사항 삭제 실패");
            }
        } catch (Exception e) {
            log.error("공지사항 삭제 실패", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 오류 발생");
        }
    }
}
