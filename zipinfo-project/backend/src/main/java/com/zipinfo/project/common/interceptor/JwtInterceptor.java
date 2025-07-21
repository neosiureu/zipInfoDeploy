package com.zipinfo.project.common.interceptor;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zipinfo.project.common.utility.JwtUtil;
import com.zipinfo.project.member.model.mapper.MemberMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private MemberMapper mapper;

    @Autowired
    private JwtUtil jwtUtil; // JWT 파싱 유틸 (subject에서 memberNo 추출용)

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return true;                       //  NPE 방지
        }

        String token = authHeader.substring(7); // "Bearer " 제거
        

        try {
            int memberNo = jwtUtil.extractMemberNo(token); // JWT에서 memberNo 추출

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

            return true; // 통과

        } catch (NumberFormatException e) {
            //  OAuth 토큰(subject가 이메일)인 경우 => 그냥 통과
            log.debug("OAuth 토큰 감지, 검증 스킵");
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
	
