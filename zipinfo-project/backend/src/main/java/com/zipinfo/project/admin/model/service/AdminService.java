package com.zipinfo.project.admin.model.service;

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

	/**
	 * 관리자 계정 생성
	 * @param member 생성할 관리자 정보
	 * @return 생성된 비밀번호
	 */
	String createAdminAccount(Member member);

	/**
	 * 이메일 중복 확인
	 * @param memberEmail 확인할 이메일
	 * @return 중복된 이메일 개수 (0이면 중복 없음)
	 */
	int checkEmail(String memberEmail);

	/**
	 * 관리자 계정 목록 조회
	 * @return 관리자 계정 목록
	 */
	List<Member> selectAdminList();
}