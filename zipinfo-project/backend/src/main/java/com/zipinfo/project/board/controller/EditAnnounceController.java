package com.zipinfo.project.board.controller;

import java.util.HashMap;
import java.util.Map;

import com.zipinfo.project.board.model.dto.Board;
import com.zipinfo.project.board.model.service.EditAnnounceService;
import com.zipinfo.project.member.model.dto.Member;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/editAnnounce")
@RequiredArgsConstructor
public class EditAnnounceController {

    private final EditAnnounceService editBoardService;

    // 글 작성 폼
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/write")
    public String showWriteForm() {
        return "announce/AnnounceWrite";
    }

    // 글 등록 처리
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/write")
    public String insertBoard(@ModelAttribute Board board,
                              @SessionAttribute("loginMember") Member loginMember,
                              RedirectAttributes ra) {

        board.setMemberNo(loginMember.getMemberNo());

        int boardNo = editBoardService.boardInsert(board);

        if (boardNo > 0) {
            ra.addFlashAttribute("message", "공지사항이 등록되었습니다.");
            return "redirect:/announce/detail/" + boardNo;
        } else {
            ra.addFlashAttribute("message", "공지사항 등록 실패");
            return "redirect:/editAnnounce/write";
        }
    }

    // 상세 조회
    @GetMapping("/detail/{boardNo}")
    public String viewBoardDetail(@PathVariable int boardNo, Model model) {
        Board board = editBoardService.selectBoard(boardNo);

        if (board == null) {
            model.addAttribute("message", "존재하지 않는 게시글입니다.");
            return "redirect:/announce";
        }

        model.addAttribute("post", board);
        return "announce/AnnounceDetail";
    }

    // 수정 폼
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/update/{boardNo}")
    public String showUpdateForm(@PathVariable int boardNo,
                                 @SessionAttribute("loginMember") Member loginMember,
                                 Model model,
                                 RedirectAttributes ra) {

        Board board = editBoardService.selectBoard(boardNo);

        if (board == null || board.getMemberNo() != loginMember.getMemberNo()) {
            ra.addFlashAttribute("message", "수정 권한이 없습니다.");
            return "redirect:/announce/detail/" + boardNo;
        }

        model.addAttribute("board", board);
        return "announce/AnnounceUpdate";
    }

    // 수정 처리
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/update/{boardNo}")
    public String updateBoard(@PathVariable int boardNo,
                              @ModelAttribute Board board,
                              @SessionAttribute("loginMember") Member loginMember,
                              RedirectAttributes ra) {

        board.setBoardNo(boardNo);
        board.setMemberNo(loginMember.getMemberNo());

        int result = editBoardService.boardUpdate(board);

        if (result > 0) {
            ra.addFlashAttribute("message", "공지사항이 수정되었습니다.");
        } else {
            ra.addFlashAttribute("message", "공지사항 수정 실패");
        }

        return "redirect:/announce/detail/" + boardNo;
    }

    // 삭제 처리
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/delete/{boardNo}")
    public String deleteBoard(@PathVariable int boardNo,
                              @SessionAttribute("loginMember") Member loginMember,
                              RedirectAttributes ra) {

        Map<String, Integer> map = new HashMap<>();
        map.put("boardNo", boardNo);
        map.put("memberNo", loginMember.getMemberNo());

        int result = editBoardService.boardDelete(map);

        if (result > 0) {
            ra.addFlashAttribute("message", "공지사항이 삭제되었습니다.");
            return "redirect:/announce";
        } else {
            ra.addFlashAttribute("message", "공지사항 삭제 실패");
            return "redirect:/announce/detail/" + boardNo;
        }
    }
}
