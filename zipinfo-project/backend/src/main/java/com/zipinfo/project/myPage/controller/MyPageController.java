package com.zipinfo.project.myPage.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.myPage.model.service.MyPageService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequestMapping("myPage")
@Slf4j
@SessionAttributes({"loginMember"})
@RestController
@RequiredArgsConstructor
public class MyPageController {

	private final MyPageService service;
	
	@GetMapping("memberInfo")
	public ResponseEntity<Object> memberInfo(HttpSession session){
		
//		try {
			
			System.out.println(1);
			Member loginMember = (Member)session.getAttribute("loginMember");
			
			Member member = service.getMemberInfo(loginMember);
			
			System.out.println(member);
			
			return ResponseEntity.status(HttpStatus.OK) // 200
				   .body(member); 
			
//		} catch (Exception e) {
//			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//				   .body("불러오는 중 예외 발생 : " + e.getMessage());
//			
//		}
	}
	
	
}
