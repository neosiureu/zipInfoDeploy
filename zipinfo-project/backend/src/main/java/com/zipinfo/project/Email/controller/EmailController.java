package com.zipinfo.project.Email.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.Email.model.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController                                            // View 반환 X → JSON
@RequestMapping("/email")                                  // React 쪽 `/email/**`와 일치
@CrossOrigin(origins = "http://localhost:5173",            // 프런트 포트 열어줌
             allowedHeaders = "*",
             allowCredentials = "true")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

	
	

    private final EmailService emailService;               // 메일 로직 담당 서비스
     //1) 인증번호 발송 (React : axios.post("/email/emailSignup", email) )   
    
    @PostMapping("/emailSignup")
    public ResponseEntity<Integer> emailSignup(@RequestBody String email) {
    	log.info("회원가입 인증 메일 발송 요청 : {}", email);


        String authKey = emailService.sendEmail("signup", email); //  인증코드 생성·발송

        return ResponseEntity.ok(authKey != null ? 1 : 0);        // 1:성공 / 0:실패
    }

     // 2) 인증번호 확인 (React : axios.post("/email/checkAuthKey", {...}) ) *
    @PostMapping("/checkAuthKey")
    public ResponseEntity<Integer> checkAuthKey(@RequestBody Map<String,String> body) {

        String email   = body.get("email");
        String authKey = body.get("authKey");

        log.info("인증코드 검증 : email={}, authKey={}", email, authKey);

        boolean matched = emailService.verifyCode(email, authKey); // 일치 여부

        return ResponseEntity.ok(matched ? 1 : 0);                 // 1:일치 / 0:불일치
    }
}
