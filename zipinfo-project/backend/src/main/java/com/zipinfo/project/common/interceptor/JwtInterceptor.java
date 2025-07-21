package com.zipinfo.project.common.interceptor;

import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.project.common.utility.JwtUtil;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.mapper.MemberMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private MemberMapper mapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return true;
        }

        String token = authHeader.substring(7);

        try {
            int memberNo = jwtUtil.extractMemberNo(token);
            String savedToken = mapper.getTokenNo(memberNo);
            
            if (!token.equals(savedToken)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                Map<String, String> body = Map.of(
                        "code", "TOKEN_MISMATCH",
                        "message", "다른 PC에서 로그인하여 로그아웃 되었습니다."
                );
                new ObjectMapper().writeValue(response.getWriter(), body);
                response.getWriter().flush();
                return false;
            }

            return true;

        } catch (NumberFormatException e) {
            // OAuth 토큰인 경우 - Security Context 설정
            log.debug("OAuth 토큰 감지, Security Context 설정");
            
            try {
                // JWT 직접 파싱 (검증 없이)
                String[] parts = token.split("\\.");
                String payload = new String(java.util.Base64.getDecoder().decode(parts[1]));
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
                
                // Member 객체 생성
                Member member = new Member();
                member.setMemberEmail((String) claims.get("sub"));
                member.setMemberNo((Integer) claims.get("memberNo"));
                member.setMemberLocation((Integer) claims.get("loc"));
                member.setMemberLogin((String) claims.get("loginType"));
                
                // Spring Security Context에 설정
                Authentication auth = new UsernamePasswordAuthenticationToken(member, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
                
                log.debug("OAuth 사용자 Security Context 설정 완료: {}", member.getMemberEmail());
                
            } catch (Exception ex) {
                log.error("OAuth 토큰 처리 실패: {}", ex.getMessage());
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("JWT 검증 실패: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            new ObjectMapper().writeValue(response.getWriter(),
                    Map.of("code", "INVALID_JWT",
                           "message", "토큰 검증 실패"));
            response.getWriter().flush();
            return false;
        }
    }
}