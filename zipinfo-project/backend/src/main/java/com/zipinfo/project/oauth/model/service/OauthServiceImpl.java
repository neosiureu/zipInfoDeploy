package com.zipinfo.project.oauth.model.service;

import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.oauth.model.mapper.OauthMapper;
import com.zipinfo.project.oauth.token.JWTProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
@Slf4j
@Service
public class OauthServiceImpl implements OauthService {
	private final OauthMapper mapper;
	private final JWTProvider jwtProvider;
	private final WebClient webClient;

	@Override
	public Member loginKakao(String kakaoAccessToken) {
	    log.info("[loginKakao] 시작: kakaoAccessToken={}", kakaoAccessToken);
	    try {
	        // 1단계: 카카오 프로필 조회
	        log.debug("→ 카카오 프로필 요청 시작");
	        Map<String, Object> profile = webClient.get()
	            .uri("https://kapi.kakao.com/v2/user/me")
	            .header("Authorization", "Bearer " + kakaoAccessToken)
	            .retrieve()
	            .bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
	            .block();
	        log.debug("← 카카오 프로필 응답 받음: {}", profile);

	        // 2단계: 이메일·닉네임 파싱
	        if (profile == null) {
	            log.error("프로필이 null 입니다.");
	            throw new IllegalStateException("카카오 프로필 조회 실패");
	        }
	        Map<String,Object> kakaoAccount = (Map)profile.get("kakao_account");
	        Map<String,Object> kakaoProps   = (Map)profile.get("properties");
	        String email    = kakaoAccount   != null ? kakaoAccount.get("email").toString() : null;
	        String nickname = kakaoProps     != null ? kakaoProps.get("nickname").toString() : null;
	        log.info("파싱된 이메일={}, 닉네임={}", email, nickname);
	        if (email == null) {
	            log.error("이메일이 null 입니다.");
	            throw new IllegalStateException("카카오 이메일 정보 없음");
	        }

	        // 3단계: 회원 조회/등록
	        Member member = mapper.selectByEmail(email);
	        log.info("DB 조회 member={}", member);
	        if (member == null) {
	            log.info("신규 회원 등록");
	            member = new Member();
	            member.setMemberEmail(email);
	            member.setMemberLogin("K");
	            member.setMemberNickname(nickname);
	            member.setMemberAuth(1);
	            member.setMemberName(nickname);
	            mapper.insertKakaoMember(member);
	            member = mapper.selectByEmail(email);
	            log.info("등록 후 member={}", member);
	        }

	        // 4단계: JWT 발급 & DB 저장
	        String jwtAccess = jwtProvider.createAccessToken(member);
	        log.info("발급된 JWT 액세스 토큰={}", jwtAccess);
	        mapper.updateAccessToken(member.getMemberEmail(), jwtAccess);
	        log.info("DB에 토큰 저장 완료");

	        // 5단계: DTO 세팅 후 반환
	        member.setAccessToken(jwtAccess);
	        member.setMemberPw(null);
	        log.info("loginKakao 완료: member={}", member);
	        return member;

	    } catch (Exception e) {
	        log.error("[loginKakao] 예외 발생", e);
	        throw e;  // 원래 예외를 그대로 던지거나, 사용자 정의 예외로 감싸도 됩니다.
	    }
	}

}
