package com.zipinfo.project.myPage.model.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.zipinfo.project.member.model.dto.Member;

@Mapper
public interface MyPageMapper {

	Member getMemberInfo();

}
