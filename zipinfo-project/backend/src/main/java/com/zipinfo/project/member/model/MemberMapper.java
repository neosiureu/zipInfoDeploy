package com.zipinfo.project.member.model;

import org.apache.ibatis.annotations.Mapper;

import com.zipinfo.project.member.model.dto.Member;


@Mapper
public interface MemberMapper {

	Member login(String memberEmail);

}
