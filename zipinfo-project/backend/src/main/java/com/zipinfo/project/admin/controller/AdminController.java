package com.zipinfo.project.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.admin.model.service.AdminService;
import com.zipinfo.project.comment.model.service.CommentService;
import com.zipinfo.project.common.config.JwtTokenProvider;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.service.MemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/admin")
@Slf4j
@RequiredArgsConstructor


public class AdminController {

    private final AdminService service;
    private final JwtTokenProvider jwtTokenProvider;
    private final CommentService commentService;
    
    @PostMapping("login")
    public ResponseEntity<?> login(@RequestBody Member inputMember) {
        Member loginMember = service.login(inputMember);
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 실패");
        }
        
	    String token = jwtTokenProvider.createAccessToken(loginMember);
	    loginMember.setAccessToken(token);            //  토큰 세팅
	    
	    return ResponseEntity.ok(loginMember); 
    }


    @DeleteMapping("/comments/{commentNo}")
    public int deleteCommentByAdmin(@PathVariable ("commentNo") int commentNo) {
		log.debug("삭제할 댓글 번호"+ commentNo);

		int deleteCommentByAdmin = service.deleteCommentByAdmin(commentNo);
    	return deleteCommentByAdmin;
    } 
    
    @DeleteMapping("/board/{boardNo}")
    public int deleteBoardByAdmin(@PathVariable ("boardNo") int boardNo) {
		log.debug("삭제할 게시글 번호"+ boardNo);

		int deleteBoardByAdmin = service.deleteBoardByAdmin(boardNo);
    	return deleteBoardByAdmin;
    } 

    /**
     * 관리자 계정 생성
     * POST /admin/createAdminAccount
     * @param member 생성할 관리자 정보
     * @return 생성된 비밀번호
     */
    @PostMapping("/createAdminAccount")
    public ResponseEntity<String> createAdminAccount(@RequestBody Member member) {
        try {
            int checkEmail = service.checkEmail(member.getMemberEmail());

            if(checkEmail > 0) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body("이미 사용중인 이메일 입니다.");
            }

            String accountPw = service.createAdminAccount(member);

            return ResponseEntity.status(HttpStatus.CREATED).body(accountPw);
        } catch (Exception e) {
            log.error("관리자 계정 생성 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("관리자 계정 생성 중 오류가 발생했습니다.");
        }
    }



    /**
     * 관리자 계정 목록 조회
     * GET /admin/adminList
     * @return 관리자 계정 목록
     */
    @GetMapping("/adminList")
    public ResponseEntity<List<Member>> getAdminList() {
        try {
            List<Member> adminList = service.selectAdminList();
            return ResponseEntity.ok(adminList);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    

}
	