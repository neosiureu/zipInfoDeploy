package com.zipinfo.project.Email.controller;

import java.util.HashMap;
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

@RestController                                            // JSON
@RequestMapping("/email")                                  // `/email`
@CrossOrigin(origins = "http://localhost:5173",            // 프런트 포트
             allowedHeaders = "*",
             allowCredentials = "true")
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    private final EmailService emailService;               // 메일 로직 담당 서비스
    Map<String,String> emailMap = new HashMap<>();
    
     //1) 인증번호 발송 (React : axios.post("/email/emailSignup", email) )   
    
    @PostMapping("/emailSignup")
    public ResponseEntity<Integer> emailSignup(@RequestBody String email) {
    	log.info("회원가입 인증 메일 발송 요청 : {}", email);


    	
        String authKey = emailService.sendEmail("signup", email); //  인증코드 생성·발송

        if(authKey==null) {
        	
        	return ResponseEntity.ok().body(0);
        }
        
        return ResponseEntity.ok().body(1);     // 1:성공  0:실패
    }

    
    
     // 2) 이메일로 보낸 인증번호가 사용자의 입력과 일치하는지 확인
    @PostMapping("/checkAuthKey")
    public ResponseEntity<Integer> checkAuthKey(@RequestBody Map<String,String> body) {
    	log.info("체크어스키 확인 컨트롤러 진입 :"+body) ;

        String email   = body.get("email");
        String authKey = body.get("authKey");

        log.info("인증코드 검증 : email={}, authKey={}", email, authKey);
        emailMap.put("email", email);
        emailMap.put("authKey", authKey);
        

        int matched = emailService.verifyCode(emailMap); // 일치 여부

        
        if(matched==0) {
        	
        	return ResponseEntity.ok().body(0);
        }

        log.info("최종검증"+matched);

        return ResponseEntity.ok().body(1);                 // true = 일치 / false = 불일치
    }
    
    
    
    
    
    
}
