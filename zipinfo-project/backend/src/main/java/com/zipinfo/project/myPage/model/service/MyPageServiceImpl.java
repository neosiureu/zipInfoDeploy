package com.zipinfo.project.myPage.model.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.myPage.model.mapper.MyPageMapper;
import com.zipinfo.project.stock.model.dto.Stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Transactional(rollbackFor = Exception.class)
@Service
@RequiredArgsConstructor
@Slf4j
public class MyPageServiceImpl implements MyPageService{

	private final MyPageMapper mapper;
	
	@Autowired
	private BCryptPasswordEncoder bcrypt;
	
	@Override
	public Member getMemberInfo(Member loginMember) {
		
			return mapper.getMemberInfo(loginMember);
		}
	
	@Override
	public int updateInfo(Member loginMember, Member member) {
		
		int result;
		
		member.setMemberNo(loginMember.getMemberNo());
		
		if(loginMember.getMemberAuth() == 3) {
			
			Member compareInfo = mapper.compareInfo(member);
			
			if(!compareInfo.getCompanyName().equals(member.getCompanyName()) || 
				!compareInfo.getCompanyName().equals(member.getCompanyLocation()) ||
				!compareInfo.getPresidentName().equals(member.getPresidentName()) ||
				!compareInfo.getPresidentPhone().equals(member.getPresidentPhone()) ||
				!compareInfo.getBrokerNo().equals(member.getBrokerNo())) {
				int changeAuth = mapper.changeAuth(member);
			}
			
			int firstResult = mapper.updateNormalInfo(member);
			
			int secondResult = mapper.updateBrokerInfo(member);
			
			result = firstResult + secondResult;
			
		}else {
			result =  mapper.updateNormalInfo(member);
		}
		
		return result;
	}
	
	@Override
	public int checkPassword(Member loginMember, Member member) {
		
		String memberPassword = mapper.getMemberPassword(loginMember);
		
		if(!bcrypt.matches(member.getMemberPw(), memberPassword)) {
			return 0;
		}
		
		return 1;
	}
	
	@Override
	public int updatePassword(Member loginMember, Member member) {
		
		String encPw = bcrypt.encode(member.getMemberPw());
		member.setMemberPw(encPw);
		member.setMemberNo(loginMember.getMemberNo());
		
		return mapper.updatePassword(member);
	}
	
	@Override
	public int checkNickname(Member loginMember, Member member) {
		
		member.setMemberNo(loginMember.getMemberNo());
		
		Integer result = mapper.checkNickname(member);
		
		return result != null ? 1: 0;
	}
	
	@Override
	public int withDraw(Member loginMember) {
		return mapper.withDraw(loginMember);
	}
	
	@Override
	public int addStock(Stock stock) {
		return mapper.addStock(stock);
	}
}
