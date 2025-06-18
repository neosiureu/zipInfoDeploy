package com.zipinfo.project.myPage.model.service;

import com.zipinfo.project.member.model.dto.Member;

public interface MyPageService {

	Member getMemberInfo(Member loginMember);

	int updateInfo(Member loginMember, Member member);

	int checkPassword(Member loginMember, Member member);

	int updatePassword(Member loginMember, Member member);


}
