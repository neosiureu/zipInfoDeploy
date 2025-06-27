package com.zipinfo.project.admin.model.service;

import java.util.List;
import com.zipinfo.project.admin.model.dto.HelpMessage;

public interface HelpMessageService {
    List<HelpMessage> getAllMessages();           // 전체 문의 목록
    HelpMessage getMessageById(Long messageNo);   // 상세 조회
}
