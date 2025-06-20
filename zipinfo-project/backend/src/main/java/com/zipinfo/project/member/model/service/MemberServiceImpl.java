package com.zipinfo.project.member.model.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.mapper.MemberMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 
 */
@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class MemberServiceImpl implements MemberService{
	
	
	@Autowired
	private MemberMapper mapper;

	@Autowired
	private BCryptPasswordEncoder bcrypt;

	
	/** 이주원
	 * 로그인 서비스
	 */
	@Override
	public Member login(Member inputMember) {
    Member loginMember = mapper.login(inputMember.getMemberEmail());
	log.info("매퍼 들어간 이후"+ loginMember);

    log.info("db에서 꺼내온 값1: {}", loginMember);
    log.info("프론트에서 온 값1: {}", inputMember);

		if(loginMember ==null) {
			 log.info("db에서 꺼내온 값2: {}", loginMember);
			    log.info("프론트에서 온 값2: {}", inputMember);
			return null;
		}
		

		if(!bcrypt.matches(inputMember.getMemberPw(), loginMember.getMemberPw())) {
			 log.info("db에서 꺼내온 값3: {}", loginMember);
			    log.info("프론트에서 온 값3: {}", inputMember);
			return null;
		}
		
		loginMember.setMemberPw(null);
		
		return loginMember;
	}

	
	
	/** 이주원 
	 * 이메일 중복 체크
	 */
	@Override
	public int checkEmail(String memberEmail) {
		// TODO Auto-generated method stub
		return mapper.checkEmail(memberEmail);

	}

	
	/** 이주원 
	 * 닉네임 중복 체크
	 */
	@Override
	public int checkNickname(String memberNickname) {

		return mapper.checkNickname(memberNickname);

	}

	
	/** 이주원 
	 * 중개사번호 중복 체크
	 */
	@Override
	public int checkBrokerNo(String brokerNo) {
		// TODO Auto-generated method stub
		return mapper.checkBrokerNo(brokerNo);
	}



	@Override
	public int findMemberNoByEmail(String loginUserEmail) {
		// TODO Auto-generated method stub
		return 0;
	}



	
	
	@Override
	public boolean isAdmin(int memberNo) {
		// TODO Auto-generated method stub
		return false;
	}



	@Override
	public ResponseEntity<Object> signup(Member member) {
    	
		member.setMemberEmail("E");
    
    	// 멤버 또는 중개사의 location 필드를 채워 넣어 DB에 들어가기 좋게 만든다.
    	
    	if(member.getBrokerNo()!=null) {
    		
    		member.setPresidentName(member.getMemberName());

    		member.setMemberLocation(member.getPostcode()+"^^^"+member.getAddress() + "^^^"+ member.getDetailAddress());
        	
        	member.setCompanyLocation(member.getCompanyPostcode()+"^^^"+member.getCompanyAddress() + "^^^"+ member.getCompanyDetailAddress());
    		
        	// log.info("중개사 권한"+member);

        	ResponseEntity<Object>  signupBroker = mapper.signupBroker();
        	
        	
        	log.info("중개사 권한"+signupBroker);
        	
    		return signupBroker;

    	}
    	
    	else {
    		
    		member.setMemberLocation(member.getPostcode()+"^^^"+member.getAddress() +"^^^"+ member.getDetailAddress());

    		//log.info("일반인 권한"+member);


    		ResponseEntity<Object> signupGeneral = mapper.signupGeneral();
    		
        	log.info("중개사 권한"+signupGeneral);

    		return signupGeneral;

    	}
	}


	
	

	
}
