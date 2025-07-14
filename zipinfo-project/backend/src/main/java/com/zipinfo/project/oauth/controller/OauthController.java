package com.zipinfo.project.oauth.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

@RestController // JSON
@RequestMapping("/oauth") // 요청주소 /oauth
@CrossOrigin(origins = "http://localhost:5173", // 프런트 포트
		allowedHeaders = "*", allowCredentials = "true")
@RequiredArgsConstructor
@Slf4j
public class OauthController {
	private final OauthService oauthService;
	private final JwtTokenProvider jwtTokenProvider;

	/** 이주원
	 * 카카오 로그인 및 회원가입 컨트롤러 로직
	 * @param body
	 * @return
	 */
	@PostMapping("/kakao")
	public ResponseEntity<Object> kakaoLogin(@RequestBody Map<String, String> body) {
		String kakaoAccessToken = body.get("code");
		try {
			Member member = oauthService.loginKakao(kakaoAccessToken);

			return ResponseEntity.ok(Map.of("loginMember", member, "accessToken", member.getAccessToken()));

		} catch (IllegalStateException e) {
			// 탈퇴 회원인 경우
			if ("MEMBER_WITHDRAWN".equals(e.getMessage())) {
				log.warn("탈퇴한 회원의 로그인 시도: {}", kakaoAccessToken);
				return ResponseEntity.status(HttpStatus.FORBIDDEN)
						.body(Map.of("msg", "MEMBER_WITHDRAWN", "message", "탈퇴한 회원은 로그인할 수 없습니다."));
			}
			throw e; // 다른 IllegalStateException은 다시 던지기

		} catch (Exception ex) {
			log.error("kakaoLogin 에러", ex);
			return ResponseEntity.internalServerError().build();
		}
	}

	
	@PostMapping("/kakaoWithdraw")
	public ResponseEntity<?> kakaoWithdraw(@AuthenticationPrincipal Member loginMember) {
	try {
			
	        if (loginMember == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
			
			int result = oauthService.withDraw(loginMember);
		
			return ResponseEntity.status(HttpStatus.OK) // 200
					.body(result); 
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("불러오는 중 예외 발생 : " + e.getMessage());
		}
	}
	
	/** 이주원
	 * 네이버 로그인 및 회원가입 로직
	 * @param body
	 * @return
	 */
	@PostMapping("/naver")
	public ResponseEntity<Map<String, Object>> naverLogin(@RequestBody Map<String, String> body) {

		String naverAccessToken = body.getOrDefault("accessToken", body.get("code"));
		log.info("네이버 로그인 요청, token={}", naverAccessToken);

		try {
			Member member = oauthService.loginNaver(naverAccessToken);
			String jwt = jwtTokenProvider.createAccessToken(member);

			Map<String, Object> result = Map.of("loginMember", member, "accessToken", jwt);
			return ResponseEntity.ok(result);

		} catch (Exception ex) {
			log.error("naverLogin 에러", ex);
			return ResponseEntity.internalServerError().build();
		}
	}

}
