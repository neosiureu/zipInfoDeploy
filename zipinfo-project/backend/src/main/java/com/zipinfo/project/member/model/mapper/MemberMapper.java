package com.zipinfo.project.member.model.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.zipinfo.project.member.model.dto.Member;


@Mapper
public interface MemberMapper {

	Member login(String memberEmail);

	int checkEmail(String memberEmail);

	int checkNickname(String memberNickname);

	int checkBrokerNo(String brokerNo);

}
