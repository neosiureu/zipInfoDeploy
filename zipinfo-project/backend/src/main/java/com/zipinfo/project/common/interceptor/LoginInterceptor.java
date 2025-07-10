package com.zipinfo.project.common.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.zipinfo.project.member.model.mapper.MemberMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Autowired
    private MemberMapper mapper;

    @Autowired
    private JwtUtil jwtUtil; // JWT 파싱 유틸 (subject에서 memberNo 추출용)

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        String token = authHeader.substring(7); // "Bearer " 제거

        try {
            int memberNo = jwtUtil.extractMemberNo(token); // JWT에서 memberNo 추출

            String savedToken = accessTokenMapper.findTokenByMemberNo(memberNo);

            if (!token.equals(savedToken)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return false;
            }

            return true; // 통과

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }
    }
}
	
