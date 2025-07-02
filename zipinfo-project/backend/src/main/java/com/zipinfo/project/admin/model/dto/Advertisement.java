package com.zipinfo.project.admin.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Advertisement {
    private int adNo;              // 광고 번호
    private int memberNo;
    private String adImgUrl;    // 광고 이미지 경로
    private String adOriginName;      // 원본명
    private String adRename;       // 변경명
    private boolean adMain;      // 메인 등록 여부
}
