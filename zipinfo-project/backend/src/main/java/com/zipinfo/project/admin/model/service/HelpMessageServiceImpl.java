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

    // 파일 다운로드를 위한 웹 경로를 application.properties 등에서 주입
    @Value("${my.message.web-path}")
    private String messageWebPath;

    /**
     * 전체 문의글 목록 조회 (삭제되지 않은 글만)
     * @return 문의글 리스트
     */
    @Override
    public List<HelpMessage> getAllMessages() {
        List<HelpMessage> messages = helpMessageMapper.selectAllMessages();
        
        // 각 메시지에 파일 URL 생성
        if (messages != null) {
            for (HelpMessage message : messages) {
                if (message.getFileRename() != null && !message.getFileRename().isEmpty()) {
                    String fullFileUrl = messageWebPath.endsWith("/")
                        ? messageWebPath + message.getFileRename()
                        : messageWebPath + "/" + message.getFileRename();
                    message.setFileUrl(fullFileUrl);
                }
            }
        }
        
        return messages;
    }

    /**
     * 메시지 번호로 단일 문의글 조회
     * 첨부파일명이 존재하면 웹에서 접근 가능한 URL 세팅 포함
     * 답변 내용도 원글 행(REPLY_CONTENT)에 포함됨
     * @param messageNo 문의글 번호
     * @return 문의글 DTO
     */
    @Override
    public HelpMessage getMessageById(int messageNo) {
        HelpMessage message = helpMessageMapper.selectMessageById(messageNo);
        if (message != null && message.getFileRename() != null && !message.getFileRename().isEmpty()) {
            // 파일 URL 생성
            String fullFileUrl = messageWebPath.endsWith("/")
                ? messageWebPath + message.getFileRename()
                : messageWebPath + "/" + message.getFileRename();
            message.setFileUrl(fullFileUrl);
            
            // 파일 존재 여부 로그 출력 (디버깅용)
            System.out.println("파일 URL 생성: " + fullFileUrl);
            System.out.println("파일명: " + message.getFileRename());
        }
        return message;
    }

    /**
     * 답변 저장
     * HELP_MESSAGE 테이블 내 원본 문의글 행에 REPLY_CONTENT, REPLY_YN='Y', RECEIVER_NO 컬럼 업데이트
     * @param message 답변 내용 DTO (messageNo, replyContent, receiverNo 포함)
     * @return 성공 여부
     */
    @Override
    @Transactional
    public boolean saveReply(HelpMessage message) {
        System.out.println("updateReply 파라미터 - messageNo: " + message.getMessageNo());
        System.out.println("updateReply 파라미터 - replyContent: " + message.getReplyContent());
        System.out.println("updateReply 파라미터 - receiverNo: " + message.getReceiverNo());

        int result = helpMessageMapper.updateReply(message);

        System.out.println("update 결과: " + result);

        return result > 0;
    }

   

    /**
     * 관리자가 답변하지 않은 문의글 리스트 조회
     * (본인(adminId)이 작성한 글 제외)
     * @param adminId 관리자 회원 번호
     * @return 미답변 문의글 리스트
     */
    @Override
    public List<HelpMessage> getUnansweredMessages(int adminId) {
        List<HelpMessage> messages = helpMessageMapper.selectUnansweredMessages(adminId);
        
        // 각 메시지에 파일 URL 생성
        if (messages != null) {
            for (HelpMessage message : messages) {
                if (message.getFileRename() != null && !message.getFileRename().isEmpty()) {
                    String fullFileUrl = messageWebPath.endsWith("/")
                        ? messageWebPath + message.getFileRename()
                        : messageWebPath + "/" + message.getFileRename();
                    message.setFileUrl(fullFileUrl);
                }
            }
        }
        
        return messages;
    }


    /**
     * 답변 메시지 번호로 원본 문의글 조회
     * 첨부파일 URL 포함 세팅
     * 답변은 별도 행이 아니므로 메시지 번호로 단순 조회
     * @param replyMessageNo 답변 메시지 번호
     * @return 원본 문의글 DTO
     */
    @Override
    public HelpMessage getOriginalByReplyMessageNo(int replyMessageNo) {
        HelpMessage original = helpMessageMapper.selectMessageById(replyMessageNo);
        if (original != null && original.getFileRename() != null && !original.getFileRename().isEmpty()) {
            // 파일 URL 생성
            String fullFileUrl = messageWebPath.endsWith("/")
                ? messageWebPath + original.getFileRename()
                : messageWebPath + "/" + original.getFileRename();
            original.setFileUrl(fullFileUrl);
            
            // 파일 존재 여부 로그 출력 (디버깅용)
            System.out.println("답변 조회 - 파일 URL 생성: " + fullFileUrl);
            System.out.println("답변 조회 - 파일명: " + original.getFileRename());
        }
        return original;
    }

    /**
     * 원글과 답변이 같은 행에 있기 때문에
     * 단일 문의 조회 메서드(getMessageById)와 동일 처리
     * @param messageNo 문의글 번호
     * @return 문의글 DTO (답변 포함)
     */
    @Override
    public HelpMessage getMessageWithReply(int messageNo) {
        return getMessageById(messageNo);
    }

    /**
     * 관리자가 답변한 문의글 리스트 조회
     * @param adminId 관리자 회원 번호
     * @return 답변 완료된 문의글 리스트
     */
    @Override
    public List<HelpMessage> getRepliedMessagesByAdmin(int adminId) {
        System.out.println("=== getRepliedMessagesByAdmin 호출됨 ===");
        System.out.println("서비스에서 받은 adminId: " + adminId);
        
        List<HelpMessage> result = helpMessageMapper.selectRepliedMessagesByAdmin(adminId);
        System.out.println("매퍼에서 조회된 결과 개수: " + (result != null ? result.size() : 0));
        
        // 각 메시지에 파일 URL 생성
        if (result != null) {
            for (HelpMessage message : result) {
                if (message.getFileRename() != null && !message.getFileRename().isEmpty()) {
                    String fullFileUrl = messageWebPath.endsWith("/")
                        ? messageWebPath + message.getFileRename()
                        : messageWebPath + "/" + message.getFileRename();
                    message.setFileUrl(fullFileUrl);
                    System.out.println("답변 목록 - 파일 URL 생성: " + fullFileUrl);
                }
            }
        }
        
        return result;
    }

	

}
