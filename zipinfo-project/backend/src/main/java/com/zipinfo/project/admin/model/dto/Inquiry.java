package com.zipinfo.project.admin.model.dto;

import lombok.Data;

@Data
public class Inquiry {
    private int inquiryId;
    private String memberEmail;
    private String question;
    private String answer;
    private String status; // 예: "답변대기", "답변완료"
}
