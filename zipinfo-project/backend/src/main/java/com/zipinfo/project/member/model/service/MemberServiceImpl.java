package com.zipinfo.project.member.model.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.member.model.MemberMapper;
import com.zipinfo.project.member.model.dto.Member;

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
		
		System.out.println(loginMember);
		if(loginMember ==null) {
			return null;
		}
		
		if(!bcrypt.matches(inputMember.getMemberPw(), loginMember.getMemberPw())) {
			return null;
		}
		
		loginMember.setMemberPw(null);
		
		return loginMember;
	}

	
	

}
