package com.zipinfo.project.oauth.controller;


import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.common.config.JwtTokenProvider;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.oauth.model.service.OauthService;

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
	 private final JwtTokenProvider jwtTokenProvider;

	  
	 @PostMapping("/kakao")
	 public ResponseEntity<?> kakaoLogin(@RequestBody Map<String,String> body) {
	     String kakaoAccessToken = body.get("code");
	     try {
	         Member member = oauthService.loginKakao(kakaoAccessToken);

	         return ResponseEntity.ok(
	             Map.of("loginMember", member,
	                    "accessToken", member.getAccessToken())
	         );

	     } catch (IllegalStateException e) {            // ← 14일 미만
	         if ("WITHDRAW_14D".equals(e.getMessage())) {
	             return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                                  .body(Map.of("msg", "WITHDRAW_14D"));
	         }
	         throw e;
	     } catch (Exception ex) {
	         log.error("kakaoLogin 에러", ex);
	         return ResponseEntity.internalServerError().build();
	     }
	 }
	     
	  @PostMapping("/naver")
	    public ResponseEntity<Map<String,Object>> naverLogin(@RequestBody Map<String,String> body) {

	        String naverAccessToken = body.getOrDefault("accessToken", body.get("code"));
	        log.info("네이버 로그인 요청, token={}", naverAccessToken);

	        try {
	            Member member = oauthService.loginNaver(naverAccessToken);
	            String jwt    = jwtTokenProvider.createAccessToken(member);

	            Map<String,Object> result = Map.of(
	                    "loginMember",  member,
	                    "accessToken",  jwt
	            );
	            return ResponseEntity.ok(result);

	        } catch (Exception ex) {
	            log.error("naverLogin 에러", ex);
	            return ResponseEntity.internalServerError().build();
	        }
	    }
	  

}
