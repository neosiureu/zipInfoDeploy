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
    private String startDate;    // 광고 시작일 (YYYY-MM-DD)
    private String endDate;      // 광고 종료일 (YYYY-MM-DD)
    private String imageUrl;     // 광고 이미지 URL (서버 저장 경로)
    private String author;       // 작성자
    private boolean main;      // 메인 등록 여부
}
