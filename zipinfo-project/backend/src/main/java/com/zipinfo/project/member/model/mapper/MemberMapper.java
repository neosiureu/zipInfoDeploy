package com.zipinfo.project.member.model.mapper;

import org.apache.ibatis.annotations.Mapper;
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



	
	
	
	

}
