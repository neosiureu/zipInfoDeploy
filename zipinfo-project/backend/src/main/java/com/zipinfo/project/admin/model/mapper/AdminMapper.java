package com.zipinfo.project.admin.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.zipinfo.project.member.model.dto.Member;

@Mapper
public interface AdminMapper {

    /**
     * 이메일로 회원 조회
     * 
     * @param memberEmail 조회할 이메일
     * @return 해당 이메일 회원 정보
     */
    Member login(String memberEmail);

    
    
	/** 이주원
	 * 관리자 댓글 삭제
	 * @param commentNo
	 * @return
	 */
	int deleteCommentByAdmin(int commentNo);



	/** 이주원
	 * 관리자 게시글 삭제
	 * @param boardNo
	 * @return
	 */
	int deleteBoardByAdmin(int boardNo);

	/**  관리자 이메일 중복 여부 검사
	 * @param memberEmail
	 * @return
	 */
	int checkEmail(String memberEmail);

	/** 관리자 계정 발급
	 * @param member
	 * @return
	 */
	int createAdminAccount(Member member);

	/**
	 * 관리자 계정 목록 조회
	 * @return 관리자 계정 목록
	 */
	List<Member> selectAdminList();

}
