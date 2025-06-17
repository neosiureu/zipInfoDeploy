package com.zipinfo.project.member.model.service;

import com.zipinfo.project.member.model.dto.Member;

public interface MemberService {

	Member getMember(Member member);

	Member login(Member inputMember);


}
