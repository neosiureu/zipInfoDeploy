package com.zipinfo.project.member.controller;

import java.util.HashMap;
import java.util.Map;

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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;





/**
 * 이주원
 * 회원에 대한 컨트롤러 클래스
 */
@RestController
@CrossOrigin(
	    origins        = "http://localhost:5173",
	    allowedHeaders = "*",
	    allowCredentials = "true"
	)
@RequestMapping("/member")						// 클라이언트에 들어오는 쿠키를 허용하겠다
@Slf4j
@SessionAttributes({"loginMember"})
@RequiredArgsConstructor
public class MemberController {
	private final MemberService service;
	
	
	/**
	 * @param session
	 * @return
	 * 이주원
	 * 세션 내 멤버의 존재 여부를 체크하는 로직
	 */
	@GetMapping("/getMember")
    public ResponseEntity<Member> getMember(HttpSession session) {

        Member member = (Member) session.getAttribute("loginMember");

        if(member==null) {
        	return null;
        
        }
        return ResponseEntity.ok(member); 
    }

	
	
	/**
	 * @param inputMember
	 * @param session
	 * @return
	 * 이주원
	 * 로그인 로직
	 */
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
	
	

	
	/**
	 * @param memberEmail
	 * @return
	 * 이주원
	 * 이메일 중복 여부 체크 로직
	 * 
	 */
	@GetMapping("/checkEmail")
	public int checkEmail(@RequestParam("memberEmail") String memberEmail) {
//		log.info(memberEmail+"이 컨트롤러에 도착했다.");
		return service.checkEmail(memberEmail);
	}
	
	
	/**
	 * @param memberNickname
	 * @return
	 * 이주원
	 * 닉네임 중복 여부 체크 로직
	 */
	@GetMapping("/checkNickname")
	public int checkNickname(@RequestParam("memberNickname") String memberNickname) {

		return service.checkNickname(memberNickname);
		
	}
	
	
	/**
	 * @param brokerNo
	 * @return
	 * 이주원
	 * 브로커 번호 중복 여부 체크 로직
	 */
	@GetMapping("/checkBrokerNo")
	public int checkBrokerNo(@RequestParam("brokerNo") String brokerNo) {
		log.info("브로커번호 체크하기 위해 진입");
 
		return service.checkBrokerNo(brokerNo);
		
	}
	
	
	
	
	
	/** 이주원
	 * 회원가입 로직
	 * @param member
	 * @return
	 */
	@PostMapping("/signup")
	public ResponseEntity<Object> signup(@RequestBody Member member) {
	    
	    try {
	        Object result = service.signup(member);
	        log.info("컨트롤러에서 본 삽입 결과"+result);
	        return ResponseEntity.status(HttpStatus.OK).body(result);
	        
	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
	    }
	}
	
	@PostMapping("/logout")
	public ResponseEntity<Map<String, String>> logout(HttpSession session, HttpServletResponse response) {
	    
	    // 1. 네이버 관련 세션 정보 명시적 삭제
	    session.removeAttribute("naverAccessToken");
	    session.removeAttribute("naverRefreshToken");
	    session.removeAttribute("naverUserInfo");
	    session.removeAttribute("naverState");
	    
	    // 2. 카카오 관련 세션 정보도 함께 삭제
	    session.removeAttribute("kakaoAccessToken");
	    session.removeAttribute("kakaoRefreshToken");
	    session.removeAttribute("kakaoUserInfo");
	    
	    // 3. 기본 로그인 정보 삭제
	    session.removeAttribute("loginMember");
	    
	    // 4. 세션 무효화
	    session.invalidate();
	    
	    // 5. 서버의 JSESSIONID 쿠키만이라도 삭제
	    Cookie jsessionCookie = new Cookie("JSESSIONID", null);
	    jsessionCookie.setPath("/");
	    jsessionCookie.setMaxAge(0);
	    response.addCookie(jsessionCookie);
	    
	    // 6. 클라이언트에게 네이버 로그아웃 필요하다고 알려주기
	    Map<String, String> result = new HashMap<>();
	    result.put("message", "서버 로그아웃 완료");
	    result.put("naverLogoutRequired", "true"); // 클라이언트에서 네이버 로그아웃 필요
	    
	    return ResponseEntity.ok(result);
	}
	
	@GetMapping("/check-session")
	public ResponseEntity<Boolean> checkSession(HttpSession session) {
	    Member loginMember = (Member) session.getAttribute("loginMember");
	    return ResponseEntity.ok(loginMember != null);
	}

}
