package com.zipinfo.project.board.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import com.zipinfo.project.board.model.dto.Board;
import com.zipinfo.project.board.model.service.BoardService;
import com.zipinfo.project.member.model.service.MemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/notice")
@Slf4j
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

    private final MemberService memberService;

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

    // 3. 공지사항 작성 (모두 접근 가능)
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

    @PutMapping("/{boardNo}")
    public ResponseEntity<String> updateNotice(@PathVariable int boardNo, @RequestBody Board notice) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String loginUserEmail = auth.getName(); // 예: 이메일 또는 로그인ID
            
            // 로그인 사용자 정보 조회 (email -> memberNo 등)
            int loginMemberNo = memberService.findMemberNoByEmail(loginUserEmail);
            
            // 게시글 작성자 정보 조회
            Board existingBoard = boardService.selectNotice(boardNo);
            if (existingBoard == null) {
                return ResponseEntity.notFound().build();
            }
            
            int writerMemberNo = existingBoard.getMemberNo();
            
            // 권한 체크: 작성자 본인 또는 관리자여야 함
            boolean isAdmin = memberService.isAdmin(loginMemberNo);
            
            if (loginMemberNo != writerMemberNo && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
            }
            
            notice.setBoardNo(boardNo);
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
    @DeleteMapping("/{boardNo}")
    public ResponseEntity<String> deleteNotice(@PathVariable int boardNo) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String loginUserEmail = auth.getName();
            int loginMemberNo = MemberService.findMemberNoByEmail(loginUserEmail);

            Board existingBoard = boardService.selectNotice(boardNo);
            if (existingBoard == null) {
                return ResponseEntity.notFound().build();
            }
            
            int writerMemberNo = existingBoard.getMemberNo();
            boolean isAdmin = MemberService.isAdmin(loginMemberNo);

            if (loginMemberNo != writerMemberNo && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한이 없습니다.");
            }

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
