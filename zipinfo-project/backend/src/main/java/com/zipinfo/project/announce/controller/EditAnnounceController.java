package com.zipinfo.project.announce.controller;

import java.util.HashMap;
import java.util.Map;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.service.EditAnnounceService;
import com.zipinfo.project.member.model.dto.Member;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/editAnnounce")
@RequiredArgsConstructor
public class EditAnnounceController {

    private final EditAnnounceService editBoardService;

    // 관리자 권한 체크
    private boolean isAdmin(Member loginMember) {
        return loginMember != null && loginMember.getMemberAuth() == 0;
    }

    /** ✅ 공지사항 작성 폼 */
    @GetMapping("/write")
    public String showWriteForm(@SessionAttribute(value = "loginMember", required = false) Member loginMember,
                                RedirectAttributes ra) {
        if (!isAdmin(loginMember)) {
            ra.addFlashAttribute("message", "권한이 없습니다.");
            return "redirect:/announce";
        }
        return "announce/AnnounceWrite";
    }

    /** ✅ 공지사항 등록 처리 */
    @PostMapping("/write")
    public String insertBoard(@ModelAttribute Announce announce,
                              @SessionAttribute(value = "loginMember", required = false) Member loginMember,
                              RedirectAttributes ra) {
        if (!isAdmin(loginMember)) {
            ra.addFlashAttribute("message", "권한이 없습니다.");
            return "redirect:/announce";
        }

        announce.setMemberNo(loginMember.getMemberNo());

        int announceNo = editBoardService.boardInsert(announce);

        if (announceNo > 0) {
            ra.addFlashAttribute("message", "공지사항이 등록되었습니다.");
            return "redirect:/announce/detail/" + announceNo;
        } else {
            ra.addFlashAttribute("message", "공지사항 등록 실패");
            return "redirect:/editAnnounce/write";
        }
    }

    /** ✅ 상세 조회 (권한 체크 X) */
    @GetMapping("/detail/{announceNo}")
    public String viewBoardDetail(@PathVariable int announceNo, Model model) {
        Announce announce = editBoardService.selectBoard(announceNo);

        if (announce == null) {
            model.addAttribute("message", "존재하지 않는 게시글입니다.");
            return "redirect:/announce";
        }

        model.addAttribute("post", announce);
        return "announce/AnnounceDetail";
    }

    /** ✅ 수정 폼 요청 (관리자만) */
    @GetMapping("/update/{announceNo}")
    public String showUpdateForm(@PathVariable int announceNo,
                                 @SessionAttribute(value = "loginMember", required = false) Member loginMember,
                                 Model model,
                                 RedirectAttributes ra) {

        if (!isAdmin(loginMember)) {
            ra.addFlashAttribute("message", "권한이 없습니다.");
            return "redirect:/announce/detail/" + announceNo;
        }

        Announce announce = editBoardService.selectBoard(announceNo);

        if (announce == null) {
            ra.addFlashAttribute("message", "존재하지 않는 게시글입니다.");
            return "redirect:/announce";
        }

        model.addAttribute("board", announce);
        return "announce/AnnounceUpdate";
    }

    /** ✅ 수정 처리 (관리자만) */
    @PostMapping("/update/{announceNo}")
    public String updateBoard(@PathVariable int announceNo,
                              @ModelAttribute Announce announce,
                              @SessionAttribute(value = "loginMember", required = false) Member loginMember,
                              RedirectAttributes ra) {

        if (!isAdmin(loginMember)) {
            ra.addFlashAttribute("message", "권한이 없습니다.");
            return "redirect:/announce/detail/" + announceNo;
        }

        announce.setAnnounceNo(announceNo);
        announce.setMemberNo(loginMember.getMemberNo());

        int result = editBoardService.boardUpdate(announce);

        if (result > 0) {
            ra.addFlashAttribute("message", "공지사항이 수정되었습니다.");
        } else {
            ra.addFlashAttribute("message", "공지사항 수정 실패");
        }

        return "redirect:/announce/detail/" + announceNo;
    }

    /** ✅ 삭제 처리 (관리자만) */
    @PostMapping("/delete/{announceNo}")
    public String deleteBoard(@PathVariable int announceNo,
                              @SessionAttribute(value = "loginMember", required = false) Member loginMember,
                              RedirectAttributes ra) {

        if (!isAdmin(loginMember)) {
            ra.addFlashAttribute("message", "권한이 없습니다.");
            return "redirect:/announce/detail/" + announceNo;
        }

        Map<String, Integer> map = new HashMap<>();
        map.put("announceNo", announceNo);
        map.put("memberNo", loginMember.getMemberNo());

        int result = editBoardService.boardDelete(map);

        if (result > 0) {
            ra.addFlashAttribute("message", "공지사항이 삭제되었습니다.");
            return "redirect:/announce";
        } else {
            ra.addFlashAttribute("message", "공지사항 삭제 실패");
            return "redirect:/announce/detail/" + announceNo;
        }
    }
}


