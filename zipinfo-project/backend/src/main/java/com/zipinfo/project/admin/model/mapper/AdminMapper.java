package com.zipinfo.project.admin.model.mapper;

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

   
}
