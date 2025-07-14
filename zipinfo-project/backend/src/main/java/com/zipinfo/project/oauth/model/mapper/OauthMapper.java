package com.zipinfo.project.oauth.model.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.member.model.dto.Member;

@Mapper
public interface OauthMapper {

	Member selectByKakaoEmail(@Param("memberEmail") String email);

	void insertKakaoMember(Member member);

	void updateAccessToken( @Param("memberEmail") String memberEmail, @Param("accessToken")  String accessToken);

	void insertNaverMember(Member member);

	Member selectByNaverEmail(@Param("memberEmail") String email);

	void createTokenTable();

	void recoverMember(int memberNo);


	int withDraw(Member loginMember);
	
}
