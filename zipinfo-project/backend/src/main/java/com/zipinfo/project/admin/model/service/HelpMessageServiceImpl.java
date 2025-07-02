package com.zipinfo.project.admin.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import com.zipinfo.project.admin.model.mapper.HelpMessageMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HelpMessageServiceImpl implements HelpMessageService {

    private final HelpMessageMapper helpMessageMapper;

    // config.properties에서 web-path 값을 주입
    @Value("${my.message.web-path}")
    private String messageWebPath;

    @Override
    public List<HelpMessage> getAllMessages() {
        return helpMessageMapper.selectAllMessages();
    }

    @Override
    public HelpMessage getHelpMessageById(int messageNo) {
        HelpMessage message = helpMessageMapper.selectMessageById(messageNo);
        if (message != null && message.getFileRename() != null && !message.getFileRename().isEmpty()) {
            String fullFileUrl = "http://localhost:8080/api/help/message/messageFile/" + message.getFileRename();
            message.setFileUrl(fullFileUrl);
        }
        return message;
    }

    @Override
    @Transactional
    public boolean saveReply(HelpMessage message) {
        HelpMessage messageResult = helpMessageMapper.selectMessageById(message.getInquiredNo());
        if (messageResult == null) {
            return false;
        }
        int result = helpMessageMapper.insertReply(message);
        int updateResult = helpMessageMapper.updateReplyYn(message.getInquiredNo());

        return result > 0 && updateResult > 0;
    }

    @Override
    @Transactional
    public boolean markMessageAsRead(int messageNo) {
        return helpMessageMapper.updateReadFlag(messageNo) > 0;
    }

    @Override
    public List<HelpMessage> getUnansweredMessages(int adminId) {
        return helpMessageMapper.selectUnansweredMessages(adminId);
    }

    @Override
    public List<HelpMessage> getAnsweredMessagesByUser(int userNo) {
        return helpMessageMapper.selectAnsweredMessagesByUser(userNo);
    }

    @Override
    public boolean updateReadFlag(int messageNo) {
        return markMessageAsRead(messageNo);
    }

    @Override
    public boolean updateReplyContent(int messageNo, String newContent) {
        int affected = helpMessageMapper.updateReplyContent(messageNo, newContent);
        return affected > 0;
    }
}