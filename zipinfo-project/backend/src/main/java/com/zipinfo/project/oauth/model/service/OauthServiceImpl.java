package com.zipinfo.project.oauth.model.service;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
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

	  @Value("${naver.client-id}")
    private String naverClientId;

    @Value("${naver.client-secret}")
    private String naverClientSecret;

	@Override
	public Member loginKakao(String kakaoAccessToken) {
		log.info("[loginKakao] 시작: kakaoAccessToken={}", kakaoAccessToken);
		try {
			// 1단계: 카카오 프로필 조회
			log.debug(" 카카오 프로필 요청 시작");
			Map<String, Object> profile = webClient.get().uri("https://kapi.kakao.com/v2/user/me")
					.header("Authorization", "Bearer " + kakaoAccessToken).retrieve()
					.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
					}).block();
			log.debug(" 카카오 프로필 응답 받음: {}", profile);

			// 2단계: 이메일·닉네임 파싱
			if (profile == null) {
				log.error("프로필이 null 입니다.");
				throw new IllegalStateException("카카오 프로필 조회 실패");
			}
			Map<String, Object> kakaoAccount = (Map) profile.get("kakao_account");
			Map<String, Object> kakaoProps = (Map) profile.get("properties");
			String email = kakaoAccount != null ? kakaoAccount.get("email").toString() : null;
			String nickname = kakaoProps != null ? kakaoProps.get("nickname").toString() : null;
			log.info("파싱된 이메일={}, 닉네임={}", email, nickname);
			if (email == null) {
				log.error("이메일이 null 입니다.");
				throw new IllegalStateException("카카오 이메일 정보 없음");
			}

			// 3단계: 회원 조회/등록
			Member member = mapper.selectByKakaoEmail(email);
			validateWithdraw(member);
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
				member = mapper.selectByKakaoEmail(email);
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
			throw e;
		}
	}

	@Override
	public Member loginNaver(String naverAccesstoken) {
		log.info("[loginNaver] 시작: NaverAccessToken={}", naverAccesstoken);
		try {
			// 1단계: 카카오 프로필 조회
			log.debug(" 네이버 프로필 요청 시작");
			Map<String, Object> root = webClient.get().uri("https://openapi.naver.com/v1/nid/me")
					.header("Authorization", "Bearer " + naverAccesstoken).retrieve()
					.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
					}).block();
			log.debug(" 네이버 프로필 응답 받음: {}", root);

			// 2단계: 이메일·닉네임 파싱
			if (root == null) {
				log.error("프로필이 null 입니다.");
				throw new IllegalStateException("네이버 프로필 조회 실패");
			}
			Map<String, Object> naverResponse = (Map) root.get("response");
			log.info("naverResp = {}", naverResponse);

			String email = naverResponse != null ? naverResponse.get("email").toString() : null;
			String name = naverResponse != null ? naverResponse.get("name").toString() : null;
			log.info("파싱된 네이버 이메일={}, 닉네임={}", email, name);
			if (email == null) {
				log.error("이메일이 null 입니다.");
				throw new IllegalStateException("네이버 이메일 정보 없음");
			}

			// 3단계: 회원 조회/등록
			Member member = mapper.selectByNaverEmail(email);
			boolean isNew = (member == null);
			log.info("DB 조회 member={}", member);
			 if (isNew) {                                   // ── 신규 회원만 처리
			        member = new Member();
			        member.setMemberEmail(email);
			        member.setMemberLogin("N");
			        member.setMemberNickname(name);
			        member.setMemberAuth(1);
			        member.setMemberName(name);

			        mapper.insertNaverMember(member);
			        member = mapper.selectByNaverEmail(email); // PK-MEMBER_NO 확보
			        mapper.createTokenTable(member.getMemberNo());   //  여기로 이동
			    }



			    // 4단계: JWT 발급 & MEMBER.ACCESS_TOKEN 갱신
			    String jwtAccess = jwtProvider.createAccessToken(member);
			    mapper.updateAccessToken(member.getMemberEmail(), jwtAccess);

			    // 5단계: TOKEN_INFO 테이블에도 같은 토큰으로 저장
			    mapper.updateTokenInfo(member.getMemberNo(), jwtAccess);

			    // 6단계: DTO 에 토큰 붙여서 반환
			    member.setAccessToken(jwtAccess);
			    return member;

		} catch (Exception e) {
			log.error("[loginNaver] 예외 발생", e);
			throw e;
		}
	}

	private void validateWithdraw(Member m) {
		if (m == null || !"Y".equals(m.getMemberDelFl()))
			return;

		// 탈퇴한 회원이면 식별 가능한 메시지로 예외 발생
		throw new IllegalStateException("MEMBER_WITHDRAWN");
	}

	@Override
	public int withDraw(Member loginMember) {
		log.debug("현재 카카오 서비스임플" + loginMember);
		return mapper.withDraw(loginMember);
	}

	/** 네이버 연결 해제 + DB 탈퇴 플래그 업데이트 */
public int withdrawNaver(Member loginMember) {
    log.info("[withdrawNaver] memberNo={}, email={}", 
             loginMember.getMemberNo(), loginMember.getMemberEmail());

    // 1) 네이버 API: 토큰 삭제(연결 해제)
    String clientId     = naverClientId;      // @Value 로 주입
    String clientSecret = naverClientSecret;  // "
    String accessToken  = loginMember.getAccessToken();   // 로그인 시 저장했던 토큰

    Map<String, Object> res = webClient.post()
        .uri(uriBuilder -> uriBuilder
            .scheme("https").host("nid.naver.com").path("/oauth2.0/token")
            .queryParam("grant_type", "delete")
            .queryParam("client_id", clientId)
            .queryParam("client_secret", clientSecret)
            .queryParam("access_token", accessToken)
            .queryParam("service_provider", "NAVER")
            .build())
        .retrieve().bodyToMono(new ParameterizedTypeReference<Map<String,Object>>() {})
        .block();

    log.info("네이버 unlink 결과 = {}", res);   // result:"success" 면 OK

    // 2) 우리 DB에서 MEMBER_DEL_FL='Y' 로 업데이트
    return mapper.withDraw(loginMember);       // 기존 쿼리 그대로 재사용
}
}
