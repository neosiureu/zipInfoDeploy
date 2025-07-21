package com.zipinfo.project.common.config;
import java.util.Date;
import java.util.List;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import org.springframework.stereotype.Component;

import com.zipinfo.project.member.model.dto.Member;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}") private String secretKey;
    @Value("${jwt.exp:1800000}") private long expMillis; // 기본 30분

    public String createAccessToken(Member member) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(member.getMemberNo())) // memberNo로부터 정보를 pk삼아 넣는다
                .claim("loginType", member.getMemberLogin())           // K N E를 넣는다.
                .claim("loc",     member.getMemberLocation())               // ⭐️ location
                .claim("nick",    member.getMemberNickname())
                .claim("email", member.getMemberEmail()) // 이메일 정보를 넣는다.
                .claim("auth", member.getMemberAuth()) // 권한 정보를 넣는다.
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expMillis))
                .signWith(Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8)),
                          SignatureAlgorithm.HS256)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parse(token);
        //Member 객체를 직접 만들어서 
        Member m = new Member();
        m.setMemberNo( Integer.parseInt(claims.getSubject()) ); // 주제
        m.setMemberEmail( (String)claims.get("email") ); // 이메일
        m.setMemberLogin( (String)claims.get("loginType") );  // K / N / E
        m.setMemberAuth( (Integer)claims.get("auth") ); // 0 관리자,1 일반인,2 중개대기자,3 중개자
        m.setMemberLocation(
        		claims.get("loc") == null ? 0 : ((Number)claims.get("loc")).intValue()
        );
        m.setMemberNickname( (String)claims.get("nick") );

        // 일반인 대기자 중개자 관리자 권한도 claim 에서 => SimpleGrantedAuthority 로 
        String role = switch (m.getMemberAuth()) {
        case 0 -> "ROLE_ADMIN";
        case 1 -> "ROLE_USER";
        case 2 -> "ROLE_WATIINGBROKER";
        case 3 -> "ROLE_BROKER";
        default -> "ROLE_USER";
        };
        
        List<GrantedAuthority> auths = List.of(
            new SimpleGrantedAuthority(role)
        );

        // principal 로 Member 를 넘김 
        return new UsernamePasswordAuthenticationToken(m, token, auths);
    }
    
    

    public boolean validate(String token) {
        try { parse(token); return true; }
        catch (JwtException | IllegalArgumentException e) { return false; }
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                   .setSigningKey(secretKey.getBytes(StandardCharsets.UTF_8))
                   .build()
                   .parseClaimsJws(token)
                   .getBody();
    }
}