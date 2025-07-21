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
   protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
           throws ServletException, IOException {

       String bearer = req.getHeader("Authorization");
       log.debug("JWT >>> {}", bearer);

       if (bearer != null && bearer.startsWith("Bearer ")) {
           String token = bearer.substring(7);

           // 1) 유효성 먼저 확인
           boolean valid = provider.validate(token);
           log.debug("token valid = {}", valid);

           if (!valid) { // 유효하지 않을 때 401 리턴
               res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token expired or invalid");
               return;
           }
           
           Claims c = provider.parse(token);
           Member m = new Member();
           
           System.out.println("응애"+c);

           try {
               // 일반 JWT 토큰 처리
               m.setMemberNo(Integer.parseInt(c.getSubject()));
               m.setMemberEmail(c.get("email", String.class));
               m.setMemberLogin(c.get("loginType", String.class));
               m.setMemberAuth(c.get("auth", Integer.class));
               m.setMemberLocation(c.get("loc") == null ? 0 : ((Number) c.get("loc")).intValue());
               m.setMemberNickname(c.get("nick", String.class));
               
               System.out.println("와이"+m);

               String role = switch (m.getMemberAuth()) {
                   case 0 -> "ROLE_ADMIN";
                   case 1 -> "ROLE_USER";
                   case 2 -> "ROLE_WATIINGBROKER";
                   case 3 -> "ROLE_BROKER";
                   default -> "ROLE_USER";
               };

               Authentication auth = new UsernamePasswordAuthenticationToken(m, null,
                       List.of(new SimpleGrantedAuthority(role)));
               SecurityContextHolder.getContext().setAuthentication(auth);
               
           } catch (NumberFormatException e) {
               // OAuth 토큰인 경우 처리
               m.setMemberNo(((Number) c.get("memberNo")).intValue());
               m.setMemberEmail(c.getSubject()); // OAuth는 subject가 이메일
               m.setMemberLogin("OAuth");
               m.setMemberAuth(1); // 일반 사용자 권한
               m.setMemberNickname((String) c.get("nickname"));
               m.setMemberLocation(c.get("loc", Integer.class));
               
               System.out.println("와이"+m);
               
               Authentication auth = new UsernamePasswordAuthenticationToken(m, null,
                       List.of(new SimpleGrantedAuthority("ROLE_USER")));
               SecurityContextHolder.getContext().setAuthentication(auth);
           }
       }

       chain.doFilter(req, res);
   }

}
