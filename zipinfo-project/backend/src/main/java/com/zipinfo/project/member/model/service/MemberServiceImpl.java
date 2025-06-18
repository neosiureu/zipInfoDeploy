package com.zipinfo.project.member.model.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.mapper.MemberMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class MemberServiceImpl implements MemberService{
	
	
	@Autowired
	private MemberMapper mapper;

	@Autowired
	private BCryptPasswordEncoder bcrypt;

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

	@Override
	public int checkEmail(String memberEmail) {
		// TODO Auto-generated method stub
		return mapper.checkEmail(memberEmail);

	}

	@Override
	public int checkNickname(String memberNickname) {

		return mapper.checkNickname(memberNickname);

	}

	@Override
	public int checkBrokerNo(String brokerNo) {
		// TODO Auto-generated method stub
		return mapper.checkBrokerNo(brokerNo);
	}
 
}
