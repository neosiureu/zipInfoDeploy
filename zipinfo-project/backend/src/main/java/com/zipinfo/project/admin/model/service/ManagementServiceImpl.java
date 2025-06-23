package com.zipinfo.project.admin.model.service;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.admin.model.dto.BrokerApplicationDTO;
import com.zipinfo.project.admin.model.mapper.ManagementMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ManagementServiceImpl implements ManagementService {

    @Autowired
    private ManagementMapper managementMapper;

    @Override
    public List<Member> getAllMembers() {
        return managementMapper.selectAllMembers();
    }

    @Override
    public List<Member> getDeletedMembers() {
        return managementMapper.selectDeletedMembers();
    }

    @Override
    public List<BrokerApplicationDTO> getBrokerApplications() {
        return managementMapper.selectBrokerApplications();
    }

    @Override
    public int updateBrokerApplicationStatus(Long memberNo, String status) {
        return managementMapper.updateBrokerApplicationStatus(memberNo, status);
    }

    @Override
    public int updateMemberAuth(Long memberNo, int authId) {
        return managementMapper.updateMemberAuth(memberNo, authId);
    }

    @Override
    public int toggleBlockMember(Long memberNo, boolean block) {
        return managementMapper.toggleBlockMember(memberNo, block);
    }

    @Override
    public int deleteMember(Long memberNo) {
        return managementMapper.deleteMember(memberNo);
    }

    @Override
    public int restoreMember(Long memberNo) {
        return managementMapper.restoreMember(memberNo);
    }
}
