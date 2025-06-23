package com.zipinfo.project.admin.model.mapper;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.admin.model.dto.BrokerApplicationDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ManagementMapper {

    List<Member> selectAllMembers();

    List<Member> selectDeletedMembers();

    List<BrokerApplicationDTO> selectBrokerApplications();

    int updateBrokerApplicationStatus(@Param("memberNo") Long memberNo, @Param("status") String status);

    int updateMemberAuth(@Param("memberNo") Long memberNo, @Param("authId") int authId);

    int toggleBlockMember(@Param("memberNo") Long memberNo, @Param("block") boolean block);

    int deleteMember(@Param("memberNo") Long memberNo);

    int restoreMember(@Param("memberNo") Long memberNo);
}
