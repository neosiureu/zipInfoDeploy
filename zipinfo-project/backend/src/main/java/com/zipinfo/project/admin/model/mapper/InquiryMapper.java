package com.zipinfo.project.admin.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.admin.model.dto.Inquiry;  // DTO import

@Mapper
public interface InquiryMapper {

    List<Inquiry> selectAllInquiries();

    Inquiry selectInquiryById(@Param("inquiryId") int inquiryId);

    int insertAnswer(Inquiry inquiry);

    int updateAnswer(Inquiry inquiry);

    int deleteInquiry(@Param("inquiryId") int inquiryId);
}
