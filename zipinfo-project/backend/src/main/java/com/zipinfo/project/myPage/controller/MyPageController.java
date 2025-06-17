package com.zipinfo.project.myPage.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.myPage.model.service.MyPageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@CrossOrigin(origins="http://localhost:5173" , allowCredentials = "true")
@RequestMapping("myPage")
@Slf4j
//@SessionAttributes({"loginMember"})
@RestController
@RequiredArgsConstructor
public class MyPageController {

	private final MyPageService service;
	
	@GetMapping("memberInfo")
	public ResponseEntity<Object> memberInfo(){
		
try {
			
			Member member = service.getMemberInfo();
			
			System.out.println(member);
			
			return ResponseEntity.status(HttpStatus.OK) // 200
				   .body(member); 
			
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				   .body("불러오는 중 예외 발생 : " + e.getMessage());
			
		}
	}
	
	
}
