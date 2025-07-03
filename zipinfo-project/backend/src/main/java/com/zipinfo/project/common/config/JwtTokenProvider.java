package com.zipinfo.project.common.config;
import java.util.Date;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
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
        UserDetails principal = User.builder()
                .username(claims.getSubject())
                .password("")                // 패스워드는 필요 없음
                .authorities("ROLE_USER")    // 또는 claims.get("auth") 로 권한 주입
                .build();

        return new UsernamePasswordAuthenticationToken(
                principal, token, principal.getAuthorities());
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
