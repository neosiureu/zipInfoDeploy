package com.zipinfo.project.admin.controller;

import java.util.ArrayList;               // 추가
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.zipinfo.project.admin.model.service.AdminService;
import com.zipinfo.project.common.config.JwtTokenProvider;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.service.MemberService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("admin")
@Slf4j
@RequiredArgsConstructor
@SessionAttributes({ "loginMember" })
public class AdminController {

    private final AdminService service;
    private final JwtTokenProvider jwtTokenProvider;
    
    @PostMapping("login")
    public ResponseEntity<?> login(@RequestBody Member inputMember) {
        Member loginMember = service.login(inputMember);
        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인 실패");
        }
        
	    String token = jwtTokenProvider.createAccessToken(loginMember);
	    loginMember.setAccessToken(token);            //  토큰 세팅
	    
	    return ResponseEntity.ok(loginMember); 
    }

	

}
	