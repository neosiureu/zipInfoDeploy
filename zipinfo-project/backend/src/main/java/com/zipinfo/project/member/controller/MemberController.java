package com.zipinfo.project.member.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.service.MemberService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@CrossOrigin(origins="http://localhost:5173" /*, allowCredentials = "true"*/) //리엑트에서의 서버와 통신할때 포트가 달라서이 오리진과도 통신하기위해서
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
	
	@PostMapping("login")
	public Member login(@RequestBody Member inputMember, Model model) {
		
		Member loginMember = service.login(inputMember);
		
		if(loginMember == null) {
			return null;
		}
			model.addAttribute(loginMember);
			return loginMember;
			
		}
	
	
}
