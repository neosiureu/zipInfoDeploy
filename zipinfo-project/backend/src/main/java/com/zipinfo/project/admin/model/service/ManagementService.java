package com.zipinfo.project.admin.model.service;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.admin.model.dto.BrokerApplicationDTO;

import java.util.List;

public interface ManagementService {

    List<Member> getAllMembers();

    List<Member> getDeletedMembers();

    List<BrokerApplicationDTO> getBrokerApplications();

    int updateBrokerApplicationStatus(Long memberNo, String status);

    int updateMemberAuth(Long memberNo, int authId);

    int toggleBlockMember(Long memberNo, boolean block);

    int deleteMember(Long memberNo);

    int restoreMember(Long memberNo);
}
