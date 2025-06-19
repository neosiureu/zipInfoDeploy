package com.zipinfo.project.member.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttributes;


import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.service.MemberService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController

@CrossOrigin(
	    origins        = "http://localhost:5173",
	    allowedHeaders = "*",
	    allowCredentials = "true"
	)
@RequestMapping("member")						// 클라이언트에 들어오는 쿠키를 허용하겠다
@Slf4j
@SessionAttributes({"loginMember"})
@RequiredArgsConstructor
public class MemberController {
	private final MemberService service;	
	
	@GetMapping("/getMember")
    public ResponseEntity<Member> getMember(HttpSession session) {
        Member member = (Member) session.getAttribute("loginMember");

        if(member==null) {
        	return null;
        
        }
        return ResponseEntity.ok(member); 
    }

	@PostMapping("/login")
	public ResponseEntity<Object> login(@RequestBody Member inputMember, HttpSession session) {
 
	    Member loginMember = service.login(inputMember);

	    if (loginMember == null) {         // 이메일 불일치 또는 비번 불일치
	        return ResponseEntity        // 401 + 메시지
	              .status(HttpStatus.UNAUTHORIZED)
	              .body("아이디 또는 비밀번호가 올바르지 않습니다.");
	    }
	   
	    session.setAttribute("loginMember", loginMember);
	    System.out.println("해당 멤버의 권한은"+loginMember.getMemberAuth()+"입니다.");
	    return ResponseEntity.ok(loginMember);
	}
	
	

	
	@GetMapping("/checkEmail")
	public int checkEmail(@RequestParam("memberEmail") String memberEmail) {
		log.info(memberEmail+"이 도착했다.");
		return service.checkEmail(memberEmail);
	}
	
	
	@GetMapping("checkNickname")
	public int checkNickname(@RequestParam("memberNickname") String memberNickname) {
		
		return service.checkNickname(memberNickname);
		
	}
	
}
