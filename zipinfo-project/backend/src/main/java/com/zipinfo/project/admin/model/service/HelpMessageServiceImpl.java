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

    // config.properties에서 웹 경로 값을 주입받음
    @Value("${my.message.web-path}")
    private String messageWebPath;

    /**
     * 전체 문의글 목록 조회
     */
    @Override
    public List<HelpMessage> getAllMessages() {
        return helpMessageMapper.selectAllMessages();
    }

    /**
     * 특정 문의글 상세 조회
     * 답변글이라면 원글에서 첨부파일 정보 가져와 세팅
     */
    @Override
    public HelpMessage getHelpMessageById(int messageNo) {
        HelpMessage message = helpMessageMapper.selectMessageById(messageNo);
        if (message != null) {
            HelpMessage targetMessage = message; // 기본은 자기 자신
            // 답변글이면 원글의 파일 정보를 사용
            if (message.getInquiredNo() > 0) {
                HelpMessage original = helpMessageMapper.selectMessageById(message.getInquiredNo());
                if (original != null) {
                    targetMessage = original;
                }
            }
            // 파일명이 있으면 다운로드 URL 세팅
            if (targetMessage.getFileRename() != null && !targetMessage.getFileRename().isEmpty()) {
                String fullFileUrl = "http://localhost:8080/api/help/message/messageFile/" + targetMessage.getFileRename();
                message.setFileRename(targetMessage.getFileRename());
                message.setFileOriginName(targetMessage.getFileOriginName());
                message.setFileNo(targetMessage.getFileNo());
                message.setFileUrl(fullFileUrl);
            }
        }
        return message;
    }

    /**
     * 답변 저장 (신규 등록)
     * 답변 등록 후 원글의 replyYn 값을 'Y'로 업데이트
     */
    @Override
    @Transactional
    public boolean saveReply(HelpMessage message) {
        // 원글 존재 여부 확인
        HelpMessage messageResult = helpMessageMapper.selectMessageById(message.getInquiredNo());
        if (messageResult == null) {
            return false;
        }
        int result = helpMessageMapper.insertReply(message);
        int updateResult = helpMessageMapper.updateReplyYn(message.getInquiredNo());

        return result > 0 && updateResult > 0;
    }

    /**
     * 문의글 읽음 상태(READ_FL)를 'Y'로 변경
     */
    @Override
    @Transactional
    public boolean updateReadFlag(int messageNo) {
        int updatedRows = helpMessageMapper.updateReadFlag(messageNo);
        return updatedRows > 0;
    }

    /**
     * 답변하지 않은 문의글 리스트 조회
     */
    @Override
    public List<HelpMessage> getUnansweredMessages(int adminId) {
        return helpMessageMapper.selectUnansweredMessages(adminId);
    }

    /**
     * 답변한 문의글 리스트 조회
     */
    @Override
    public List<HelpMessage> getAnsweredMessagesByUser(int userNo) {
        return helpMessageMapper.selectAnsweredMessagesByUser(userNo);
    }

    /**
     * 답변 메시지 번호로 원본 문의글 조회
     * 첨부파일 URL 포함 세팅
     */
    @Override
    public HelpMessage getOriginalByReplyMessageNo(int replyMessageNo) {
        HelpMessage original = helpMessageMapper.selectOriginalByReplyMessageNo(replyMessageNo);
        if (original != null) {
            if (original.getFileRename() != null && !original.getFileRename().isEmpty()) {
                String fullFileUrl = messageWebPath + original.getFileRename();
                original.setFileUrl(fullFileUrl);
            }
        }
        return original;
    }

    /**
     * 문의글과 해당 문의글에 달린 답변을 함께 조회
     */
    @Override
    public HelpMessage getMessageWithReply(int messageNo) {
        HelpMessage original = helpMessageMapper.selectMessageById(messageNo); // 원글 조회
        HelpMessage reply = helpMessageMapper.selectReplyByInquiredNo(messageNo); // 답변 조회 (inquiredNo = 원글 번호)

        if (reply != null) {
            original.setReplyContent(reply.getMessageContent());
        }

        // 파일 URL 세팅 등 추가 작업도 여기서 해도 됩니다.
        return original;
    }


}
