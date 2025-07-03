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
                                    FilterChain chain)
                                    throws ServletException, IOException {

        String bearer = req.getHeader("Authorization");
        if (bearer != null && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            if (provider.validate(token)) {
                Claims c = provider.parse(token);          
                Member loginMember = new Member();         
                loginMember.setMemberNo(      Integer.parseInt(c.getSubject()));
                loginMember.setMemberEmail(   c.get("email",   String.class));
                loginMember.setMemberAuth(    c.get("auth",    Integer.class));
                loginMember.setMemberLocation(c.get("loc",     Integer.class));
                loginMember.setMemberNickname(c.get("nick",    String.class));
                loginMember.setMemberLogin(   c.get("loginTp", String.class));
                Authentication auth =  new UsernamePasswordAuthenticationToken( loginMember, null,List.of(new SimpleGrantedAuthority("ROLE_USER")));                     
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        // else: 세션 기반 인증이 이미 Spring Security Filter 이후 단계에서 처리됨

        chain.doFilter(req, res);
    }
}
