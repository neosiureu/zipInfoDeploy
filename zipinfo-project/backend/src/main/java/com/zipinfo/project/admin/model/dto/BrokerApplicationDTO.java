package com.zipinfo.project.admin.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class BrokerApplicationDTO {
    private Long memberNumber;
    private String memberId;
    private String memberName;
    private String memberRole;
    private String applyStatus;  // 신청 상태 (예: PENDING, APPROVED, REJECTED 등)
    private String applyDate;
}
