package com.zipinfo.project.admin.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.zipinfo.project.member.model.dto.Member;

@Mapper
public interface AdminMapper {

	
	/** 리스트 
	 * @return
	 */
	List<Member> selectWithdrawnMemberList();

	/** 회원 탈퇴 
	 * @param memberNo
	 * @return
	 */
	int restoreWithdrawnMember(Long memberNo);

	/** 로그인 
	 * @param inputMember
	 * @return
	 */
	Member login(Member inputMember);

}
