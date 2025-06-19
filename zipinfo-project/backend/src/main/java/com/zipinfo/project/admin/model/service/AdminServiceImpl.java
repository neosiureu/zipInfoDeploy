package com.zipinfo.project.admin.model.service;
import java.util.List;

import org.springframework.stereotype.Service;

import com.zipinfo.project.admin.model.mapper.AdminMapper;
import com.zipinfo.project.member.model.dto.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

	private final AdminMapper mapper;

	@Override
	public List<Member> selectWithdrawnMemberList() {
		return mapper.selectWithdrawnMemberList();
	}

	@Override
	public boolean restoreWithdrawnMember(Long memberNo) {
		return mapper.restoreWithdrawnMember(memberNo) > 0;
	}

	@Override
	public Member login(Member inputMember) {

		  return mapper.login(inputMember);
	}
}
