package com.zipinfo.project.admin.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.admin.model.dto.Inquiry;
import com.zipinfo.project.admin.model.service.AdminInquiryService;

@RestController
@RequestMapping("/admin/inquiry")
public class AdminInquiryController {

    @Autowired
    private AdminInquiryService inquiryService;

    // 1. 모든 문의 목록 조회
    @GetMapping("/list")
    public List<Inquiry> getAllInquiries() {
        return inquiryService.getAllInquiries();
    }

    // 2. 특정 문의 상세 조회
    @GetMapping("/{inquiryId}")
    public Inquiry getInquiryById(@PathVariable int inquiryId) {
        return inquiryService.getInquiryById(inquiryId);
    }

    // 3. 문의 답변 등록 및 수정
    @PutMapping("/reply/{inquiryId}")
    public Inquiry replyInquiry(@PathVariable int inquiryId, @RequestBody Inquiry inquiry) {
        inquiry.setInquiryId(inquiryId);
        return inquiryService.replyToInquiry(inquiry);
    }

    // 4. 문의 삭제
    @DeleteMapping("/delete/{inquiryId}")
    public String deleteInquiry(@PathVariable int inquiryId) {
        boolean deleted = inquiryService.deleteInquiry(inquiryId) > 0;
        return deleted ? "삭제 성공" : "삭제 실패";
    }

}
