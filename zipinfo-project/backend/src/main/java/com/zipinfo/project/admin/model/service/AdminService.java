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
}