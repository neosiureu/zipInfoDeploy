package com.zipinfo.project.member.model.service;

import com.zipinfo.project.member.model.dto.Member;

public interface MemberService {
	Member login(Member inputMember);

	int checkEmail(String memberEmail);

	int checkNickname(String memberNickname);

	int checkBrokerNo(String brokerNo);
}
