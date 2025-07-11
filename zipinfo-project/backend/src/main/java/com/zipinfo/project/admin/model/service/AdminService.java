package com.zipinfo.project.admin.model.service;

import java.sql.Date;
import java.util.List;

import com.zipinfo.project.member.model.dto.Member;

public interface AdminService {

	/**
	 * @param inputMember
	 * @return
	 */
	Member login(Member inputMember);

	int deleteCommentByAdmin(int commentNo);

	int deleteBoardByAdmin(int boardNo);

	/** 관리자 이메일이 중복 여부 검사
	 * @param memberEmail
	 * @return
	 */
	int checkEmail(String memberEmail);

	/** 관리자 계정 발급
	 * @param member
	 * @return
	 */
	String createAdminAccount(Member member);


	/**
	 * 관리자 계정 목록 조회
	 * @return 관리자 계정 목록
	 */
	List<Member> selectAdminList();
	
}