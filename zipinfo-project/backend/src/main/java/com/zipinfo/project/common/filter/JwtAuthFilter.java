package com.zipinfo.project.common.filter;

import com.zipinfo.project.common.config.JwtTokenProvider;
import com.zipinfo.project.member.model.dto.Member;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	@Autowired
    private final JwtTokenProvider provider;

	@Override
	protected void doFilterInternal(HttpServletRequest req,
	                                HttpServletResponse res,
	                                FilterChain chain) throws ServletException, IOException {

	    String bearer = req.getHeader("Authorization");
	    log.debug("JWT  >>> {}", bearer);

	    if (bearer != null && bearer.startsWith("Bearer ")) {
	        String token = bearer.substring(7);

	        // 1) 유효성 먼저 확인
	        boolean valid = provider.validate(token);   // → parseClaimsJws: 만료면 false
	        log.debug("token valid = {}", valid);

	        if (valid) {
	            // 2) 이제 안전하게 Claims 꺼내도 Exception 발생 안함
	            Claims c = provider.parse(token);

	            Member m = new Member();
	            m.setMemberNo( Integer.parseInt(c.getSubject()) );
	            m.setMemberEmail(   c.get("email",     String.class) );
	            m.setMemberLogin(   c.get("loginType", String.class) ); // ← **loginType 로 수정**
	            m.setMemberAuth(    c.get("auth",      Integer.class) );
	            m.setMemberLocation(
	                    c.get("loc") == null ? 0 : ((Number)c.get("loc")).intValue()
	            );
	            m.setMemberNickname( c.get("nick", String.class) );

	            Authentication auth =
	                new UsernamePasswordAuthenticationToken(
	                        m, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

	            SecurityContextHolder.getContext().setAuthentication(auth);
	        }
	    }
	    chain.doFilter(req, res);
	}

}
