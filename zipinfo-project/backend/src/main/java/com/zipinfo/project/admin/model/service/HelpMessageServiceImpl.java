package com.zipinfo.project.admin.model.service;

import java.util.List;
import org.springframework.stereotype.Service;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import com.zipinfo.project.admin.model.mapper.HelpMessageMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HelpMessageServiceImpl implements HelpMessageService {

    private final HelpMessageMapper helpMessageMapper;

    @Override
    public List<HelpMessage> getAllMessages() {
        return helpMessageMapper.selectAllMessages(); // 전체 목록
    }

    @Override
    public HelpMessage getMessageById(Long messageNo) {
        return helpMessageMapper.selectMessageById(messageNo); // 상세 보기
    }
}
