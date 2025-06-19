package com.zipinfo.project.member.model.service;

import java.util.List;

import com.zipinfo.project.member.model.dto.Member;

public interface MemberService {
	Member login(Member inputMember);

	int checkEmail(String memberEmail);

	int checkNickname(String memberNickname);


	/** 관리자 권한 부여
	 * @param memberNo
	 * @param newAuth
	 * @return
	 */
	static boolean updateMemberAuth(Long memberNo, int newAuth) {
		// TODO Auto-generated method stub
		return false;
	}

	/** 관리자 페이지 목록 조회
	 * @param i
	 * @return
	 */
	static List<Member> getMembersByAuth(int i) {
		// TODO Auto-generated method stub
		return null;
	}

	int checkBrokerNo(String brokerNo);

}
