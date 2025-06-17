package com.zipinfo.project.member.model.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.member.model.dto.Member;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Transactional(rollbackFor = Exception.class)
@Service
@RequiredArgsConstructor
@Slf4j
public class MemberServiceImpl implements MemberService{
	@Override
	public Member login(Member inputMember) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
		public Member getMember(Member member) {
			// TODO Auto-generated method stub
			return null;
		}

}
