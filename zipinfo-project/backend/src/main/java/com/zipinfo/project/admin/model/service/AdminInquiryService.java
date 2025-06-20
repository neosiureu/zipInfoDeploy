package com.zipinfo.project.admin.model.service;

import java.util.List;
import com.zipinfo.project.admin.model.dto.Inquiry; // DTO로 import

public interface AdminInquiryService {
    List<Inquiry> getAllInquiries();

    Inquiry getInquiryById(int inquiryId);

    int insertAnswer(Inquiry inquiry);

    int updateAnswer(Inquiry inquiry);

    int deleteInquiry(int inquiryId);
    
    Inquiry replyToInquiry(Inquiry inquiry);
}
