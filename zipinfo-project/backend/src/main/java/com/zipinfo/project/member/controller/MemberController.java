package com.zipinfo.project.member.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.zipinfo.project.announce.controller.AnnounceController;
import com.zipinfo.project.common.config.JwtTokenProvider;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.service.MemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 이주원 회원에 대한 컨트롤러 클래스
 */
@RestController
@CrossOrigin(
	    origins = {
	        "http://localhost:5173",        // 개발 환경
	        "http://zipinfo.site",          // 프로덕션
	        "https://zipinfo.site",         // 프로덕션 HTTPS
	        "http://www.zipinfo.site",      // www 포함
	        "https://www.zipinfo.site"      // www 포함 HTTPS
	    }, 
	    allowedHeaders = "*", 
	    allowCredentials = "true"
	)@RequestMapping("/member") // 클라이언트에 들어오는 쿠키를 허용하겠다
@Slf4j
@RequiredArgsConstructor
public class MemberController {

    private final AnnounceController announceController;
	private final MemberService service;
	private final JwtTokenProvider jwtTokenProvider;

 
	/**
	 * @param session
	 * @return 이주원 세션 내 멤버의 존재 여부를 체크하는 로직
	 */
	@GetMapping("/getMember")
	public ResponseEntity<Member> getMember(@AuthenticationPrincipal Member loginMember) {

		return ResponseEntity.ok(loginMember);
	}

	/**
	 * @param inputMember
	 * @param session
	 * @return 이주원 로그인 로직
	 */
	@PostMapping("/login")
	public ResponseEntity<Object> login(@RequestBody Member inputMember) {

		Map<String, Object> loginMember = service.login(inputMember);
		if (loginMember == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("아이디 또는 비밀번호가 올바르지 않습니다.");
		}

		return ResponseEntity.ok(loginMember);
	}

	/**
	 * @param memberEmail
	 * @return 이주원 이메일 중복 여부 체크 로직
	 * 
	 */
	@GetMapping("/checkEmail")
	public int checkEmail(@RequestParam("memberEmail") String memberEmail) {
		log.info(memberEmail + "이 컨트롤러에 도착했다.");
		log.info(service.checkEmail(memberEmail) + "을 프론트단으로 보내겠다.");
		return service.checkEmail(memberEmail);
	}

	/**
	 * @param memberNickname
	 * @return 이주원 닉네임 중복 여부 체크 로직
	 */
	@GetMapping("/checkNickname")
	public int checkNickname(@RequestParam("memberNickname") String memberNickname) {

		return service.checkNickname(memberNickname);

	}

	/**
	 * @param brokerNo
	 * @return 이주원 브로커 번호 중복 여부 체크 로직
	 */
	@GetMapping("/checkBrokerNo")
	public int checkBrokerNo(@RequestParam("brokerNo") String brokerNo) {
		log.info("브로커번호 체크하기 위해 진입");

		return service.checkBrokerNo(brokerNo);

	}

	/**
	 * 이주원 회원가입 로직
	 * 
	 * @param member
	 * @return
	 */
	@PostMapping("/signup")
	public ResponseEntity<Object> signup(@RequestBody Member member) {

		try {
			Object result = service.signup(member);
			log.info("컨트롤러에서 본 삽입 결과" + result);
			return ResponseEntity.status(HttpStatus.OK).body(result);

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

	@PostMapping("/logout")
	public ResponseEntity<Map<String, String>> logout(
			@RequestHeader(value = "Authorization", required = false) String authHeader, @AuthenticationPrincipal Member loginMember) {

		// 1) 서버 측 블랙리스트 – 만약 필요할 때만 
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			String token = authHeader.substring(7);
			// jwtBlacklistService.add(token); // 만료 시각까지 저장
		}

		// 2) 클라이언트가 할 일
		Map<String, String> result = new HashMap<>();
		result.put("message", "서버 로그아웃 완료");
		result.put("naverLogoutRequired", "true"); // 네이버 팝업 로그아웃 지시
		
		if(loginMember.getMemberLogin().equals("K")) {
			result.put("kakaoLogoutRequired", "true"); // 카카오 SDK logout 지시
		}

		return ResponseEntity.ok(result);
	}
	
	
	

	@GetMapping("/check-session")
	public ResponseEntity<Boolean> checkSession(Authentication auth) {
		return ResponseEntity.ok(auth != null && auth.isAuthenticated());
	}

	
	
	
	@PostMapping("/setPw")
	public ResponseEntity<Object> setPw(@RequestBody Member member) {
		log.debug("멤버의 컨트롤러 도달 결과는" + member);

		if (member.getMemberEmail() == null) {
			return ResponseEntity.ok().body(0);
		}

		try {
			// 비크립트를 이용한 비밀번호 암호화
			String encodedPassword = service.encode(member);
			member.setMemberPw(encodedPassword);

			// DB 업데이트
			int result = service.updatePassword(member);
			
			member.setMemberPw(null);

			return ResponseEntity.ok().body(result > 0 ? 1 : 0);
		} catch (Exception e) {
			log.error("서버측 비밀번호 변경 오류", e);
			return ResponseEntity.ok().body(0);
		}
	}

	/** 이주원
	 * 가입회원 차트 데이터 조회
	 */
	@GetMapping("/signupChart")
	public ResponseEntity<List<Map<String, Object>>> signupChart() {
		try {
			List<Map<String, Object>> signupData = service.signupChart();
			log.debug("회원가입 차트에서 가져온 내용"+signupData);
			return ResponseEntity.ok(signupData);
		} catch (Exception e) {
			log.error("가입회원 차트 조회 오류", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}

	/** 이주원
	 * 탈퇴회원 차트 데이터 조회  
	 */
	@GetMapping("/withdrawChart")
	public ResponseEntity<List<Map<String, Object>>> withDrawChart() {
		try {
			List<Map<String, Object>> withdrawData = service.withDrawChart();
			log.debug("탈퇴회원 차트에서 가져온 내용"+withdrawData);
			return ResponseEntity.ok(withdrawData);
		} catch (Exception e) {
			log.error("탈퇴회원 차트 조회 오류", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
		}
	}
}
