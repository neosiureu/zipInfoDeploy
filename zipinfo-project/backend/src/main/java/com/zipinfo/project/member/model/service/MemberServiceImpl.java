package com.zipinfo.project.member.model.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.announce.controller.AnnounceController;
import com.zipinfo.project.common.config.JwtTokenProvider;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.mapper.MemberMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

	@Autowired
	private MemberMapper mapper;

	@Autowired
	private BCryptPasswordEncoder bcrypt;
	
	private final JwtTokenProvider jwtTokenProvider;

	/**
	 * 이주원 로그인 서비스
	 */
	@Override
	public Map<String, Object> login(Member inputMember) {

		inputMember.setMemberLogin("E");
		Member loginMember = mapper.login(inputMember);

		log.info("매퍼 들어간 이후" + loginMember);

		log.info("db에서 꺼내온 값1: {}", loginMember);
		log.info("프론트에서 온 값1: {}", inputMember);

		if (loginMember == null) {
			log.info("db에서 꺼내온 값2: {}", loginMember);
			log.info("프론트에서 온 값2: {}", inputMember);
			return null;
		}

		if (!bcrypt.matches(inputMember.getMemberPw(), loginMember.getMemberPw())) {
			log.info("db에서 꺼내온 값3: {}", loginMember);
			log.info("프론트에서 온 값3: {}", inputMember);
			return null;
		}
		
		loginMember.setMemberPw(null);
		
		// 2) JWT 발급
		String token = jwtTokenProvider.createAccessToken(loginMember);
		
		int memberNo = loginMember.getMemberNo();
		
		int setToken = mapper.setTokenInfo(memberNo, token);

		// 3) 응답
		Map<String, Object> body = new HashMap<>();
		body.put("loginMember", loginMember);
		body.put("accessToken", token);

		return body;
	}

	/**
	 * 이주원 이메일 중복 체크
	 */
	@Override
	public int checkEmail(String memberEmail) {
		// TODO Auto-generated method stub
		log.debug("이메일 체크중입니다" + memberEmail);
		return mapper.checkEmail(memberEmail);

	}

	/**
	 * 이주원 닉네임 중복 체크
	 */
	@Override
	public int checkNickname(String memberNickname) {

		return mapper.checkNickname(memberNickname);

	}

	/**
	 * 이주원 중개사번호 중복 체크
	 */
	@Override
	public int checkBrokerNo(String brokerNo) {
		// TODO Auto-generated method stub
		return mapper.checkBrokerNo(brokerNo);
	}

	@Override
	public boolean isAdmin(int memberNo) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public int signup(Member member) {
		
		int memberNo = 0;

		member.setMemberLogin("E"); // 이 로직에서 회원가입하는건 공통적으로 이메일 회원가입이니까
		// 멤버 또는 중개사의 location 필드를 채워 넣어 DB에 들어가기 좋게 만든다.

		if (member.getBrokerNo() != null) {

			member.setPresidentName(member.getMemberName());

			member.setCompanyLocation(member.getCompanyPostcode() + "^^^" + member.getCompanyAddress() + "^^^"
					+ member.getCompanyDetailAddress());

			member.setMemberAuth(2);

			String encPw = bcrypt.encode(member.getMemberPw());

			member.setMemberPw(encPw);

			int signupGeneral = mapper.signupGeneral(member);
			
			memberNo = member.getMemberNo();
			
			int createTokenTable = mapper.createTokenTable(memberNo);

			log.info("일반인 매퍼 들어간 후 결과" + signupGeneral);

			int signupBroker = mapper.signupBroker(member);

			log.info("중개사 매퍼 들어간 후 결과" + signupBroker);

			return signupBroker;

		}

		else {

			member.setMemberAuth(1);

			String encPw = bcrypt.encode(member.getMemberPw());

			member.setMemberPw(encPw);

			log.info("매퍼 들어가기 전의 일반인 권한" + member);

			int signupGeneral = mapper.signupGeneral(member);
			
			memberNo = member.getMemberNo();
			
			int createTokenTable = mapper.createTokenTable(memberNo);

			log.info("매퍼와 DB 후의 일반인 권한" + signupGeneral);

			return signupGeneral;

		}
		
	}

	@Override
	public String encode(Member member) {

		bcrypt.encode(member.getMemberPw());

		if (member.getMemberPw() == null) {
			return null;
		}

		return bcrypt.encode(member.getMemberPw());

	}

	@Override
	public int updatePassword(Member member) {
		
		member.setMemberLogin("E");
		log.debug("매퍼에 들어가기 전 member  정보"+ member);
		int result = mapper.updatePassword(member);

		
		return result;
	}

	@Override
	public List<Map<String, Object>> signupChart() {
		// TODO Auto-generated method stub
		return mapper.signupChart();
	}

	@Override
	public List<Map<String, Object>> withDrawChart() {
		// TODO Auto-generated method stub
		return mapper.withdrawChart();
	}

}
