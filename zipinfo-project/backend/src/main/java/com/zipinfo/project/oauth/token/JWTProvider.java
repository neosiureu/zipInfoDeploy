package com.zipinfo.project.oauth.token;
// 카카오톡 및 네이버 로그인용임. 일반 이메일할 때 jwt는 따로 있음 JWT토큰 프로바이더를 참고 
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.zipinfo.project.member.model.dto.Member;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@Component
public class JWTProvider {

    // application.properties에서 삽입함
    @Value("${jwt.secret}")
    private String secret;      

    @Value("${jwt.access.expiration-ms}")
    private long accessTokenValidity;   // 예: 30 * 60 * 1000 (30분)

    @Value("${jwt.refresh.expiration-ms}")
    private long refreshTokenValidity;  // 예: 14 * 24 * 60 * 60 * 1000 (2주)

    private SecretKey getSigningKey() {
        // 비밀키를 키 객체로 변환
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String createAccessToken(Member member) {
        Date now = new Date();
        return Jwts.builder()
            .setSubject(member.getMemberEmail())             // 토큰 소유자
            .claim("memberNo", member.getMemberNo())          // 추가 정보
            .claim("nickname", member.getMemberNickname())
            .setIssuedAt(now)                                 // 발급 시간
            .setExpiration(new Date(now.getTime() + accessTokenValidity))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    public String createRefreshToken(Member member) {
        Date now = new Date();
        return Jwts.builder()
            .setSubject(member.getMemberEmail())
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + refreshTokenValidity))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }
}
