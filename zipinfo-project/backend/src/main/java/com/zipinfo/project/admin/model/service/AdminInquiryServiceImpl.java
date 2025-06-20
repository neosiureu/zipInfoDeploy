package com.zipinfo.project.admin.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.zipinfo.project.admin.model.dto.Inquiry;  // DTO로 import
import com.zipinfo.project.admin.model.mapper.InquiryMapper;

@Service
public class AdminInquiryServiceImpl implements AdminInquiryService {

    @Autowired
    private InquiryMapper inquiryMapper;

    @Override
    public List<Inquiry> getAllInquiries() {
        return inquiryMapper.selectAllInquiries();
    }

    @Override
    public Inquiry getInquiryById(int inquiryId) {
        return inquiryMapper.selectInquiryById(inquiryId);
    }

    @Override
    public int insertAnswer(Inquiry inquiry) {
        return inquiryMapper.insertAnswer(inquiry);
    }

    @Override
    public int updateAnswer(Inquiry inquiry) {
        return inquiryMapper.updateAnswer(inquiry);
    }

    @Override
    public int deleteInquiry(int inquiryId) {
        return inquiryMapper.deleteInquiry(inquiryId);
    }
    @Override
    public Inquiry replyToInquiry(Inquiry inquiry) {
        int result = inquiryMapper.updateAnswer(inquiry);
        if (result > 0) {
            return inquiryMapper.selectInquiryById(inquiry.getInquiryId());
        } else {
            return null;
        }
    }
}
