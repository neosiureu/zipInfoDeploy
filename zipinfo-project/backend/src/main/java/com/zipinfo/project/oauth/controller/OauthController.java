package com.zipinfo.project.oauth.controller;


import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.oauth.model.service.OauthService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController                                            // JSON
@RequestMapping("/oauth")                                  // 요청주소 /oauth
@CrossOrigin(origins = "http://localhost:5173",            // 프런트 포트
             allowedHeaders = "*",
             allowCredentials = "true")
@RequiredArgsConstructor
@Slf4j
public class OauthController {
	  private final OauthService oauthService;
	  
	  
	    @PostMapping("/kakao")
	    public ResponseEntity<Member> kakaoLogin(@RequestBody Map<String,String> body , HttpSession session) {
	        String token = body.get("code");  // 실제 키 이름도 반드시 확인!
	        log.info("카카오 로그인 요청, code={}", token);

	        try {
	          Member member = oauthService.loginKakao(token);
	          session.setAttribute("loginMember", member); 
	          log.info("프론트로 보낼 member={}", member);
	          return ResponseEntity.ok(member);

	        } catch (Exception ex) {
	         
	          log.error("kakaoLogin 에러 발생", ex);
	          return ResponseEntity.status(500).build();
	        }
	    }
	  
	  

}
