package com.zipinfo.project.admin.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Advertisement {
    private int id;              // 광고 ID (PK)
    private String title;        // 광고 제목
    private String content;      // 광고 내용
    private String startDate;    // 광고 시작일 (String 혹은 Date로 변경 가능)
    private String endDate;      // 광고 종료일
    private String imageUrl;     // 광고 이미지 URL
}
