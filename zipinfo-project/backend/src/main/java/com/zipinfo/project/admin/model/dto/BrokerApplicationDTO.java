// src/main/java/com/zipinfo/project/admin/model/dto/BrokerApplicationDTO.java

package com.zipinfo.project.admin.model.dto;

import java.util.Date;

public class BrokerApplicationDTO {
    private Integer memberNumber;
    private String memberId;
    private Date joinDate;
    private Integer memberRole;
    private Integer postCount;
    private String applicationStatus;
    
    // getters/setters
    public int getMemberNumber() { return memberNumber; }
    public void setMemberNumber(int memberNumber) { this.memberNumber = memberNumber; }

    public String getMemberId() { return memberId; }
    public void setMemberId(String memberId) { this.memberId = memberId; }

    public Date getJoinDate() { return joinDate; }
    public void setJoinDate(Date joinDate) { this.joinDate = joinDate; }

    public int getMemberRole() { return memberRole; }
    public void setMemberRole(int memberRole) { this.memberRole = memberRole; }

    public int getPostCount() { return postCount; }
    public void setPostCount(int postCount) { this.postCount = postCount; }

    public String getApplicationStatus() { return applicationStatus; }
    public void setApplicationStatus(String applicationStatus) { this.applicationStatus = applicationStatus; }
}
