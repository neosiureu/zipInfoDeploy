package com.zipinfo.project.admin.model.dto;

import lombok.Data;
import java.util.Date;

@Data
public class Member {
    private int memberNumber;
    private String memberId;
    private String memberRole;
    private Date joinDate;
    private Date lastLoginDate;
    private int postCount;
    private boolean isBlocked;
}
