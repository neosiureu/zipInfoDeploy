package com.zipinfo.project.member.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.http.ResponseEntity;

import com.zipinfo.project.member.model.dto.Member;


@Mapper
public interface MemberMapper {

	Member login(Member inputMember);

	int checkEmail(String memberEmail);

	int checkNickname(String memberNickname);

	int checkBrokerNo(String brokerNo);

	int signupBroker(Member member);

	int signupGeneral(Member member);

	Member selectByEmail(String email);

	int updatePassword(Member member);

	int createTokenTable(int memberNo);

	int setTokenInfo(@Param("memberNo") int memberNo,
            @Param("token") String token);

	String getTokenNo(int memberNo);

	List<Map<String, Object>> signupChart();

	List<Map<String, Object>> withdrawChart();



	
	
	
	

}
