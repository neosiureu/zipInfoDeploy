package com.zipinfo.project.admin.model.service;

import java.util.List;

import com.zipinfo.project.member.model.dto.Member;

public interface AdminService {
	
	List<Member> selectWithdrawnMemberList();

	boolean restoreWithdrawnMember(Long memberNo);

	Member login(Member inputMember);
} 