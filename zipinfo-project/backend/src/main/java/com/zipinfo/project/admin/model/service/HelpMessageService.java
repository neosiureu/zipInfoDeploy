package com.zipinfo.project.admin.model.service;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import java.util.List;

public interface HelpMessageService {

    /**
     * 전체 문의 목록 조회
     * @return 문의글 리스트
     */
    List<HelpMessage> getAllMessages();

    /**
     * 특정 문의글 상세 조회
     * @param messageNo 조회할 문의글 번호
     * @return 해당 문의글 정보, 없으면 null
     */
    HelpMessage getHelpMessageById(int messageNo);

    /**
     * 답변 저장 (신규 등록 또는 기존 답변 수정)
     * @param messageNo 답변할 문의글 번호
     * @param replyContent 저장할 답변 내용
     * @return 성공 시 true, 실패 시 false 반환
     */
    boolean saveReply(HelpMessage message);

    /**
     * 문의글 읽음 상태(READ_FL)를 'Y'로 변경
     * @param messageNo 읽음 처리할 문의글 번호
     * @return 성공 시 true, 실패 시 false 반환
     */
    boolean updateReadFlag(int messageNo);

    /**
     * 답변하지 않은 문의글 리스트 조회
     * @param adminId 관리자 ID (필요한 경우)
     * @return 답변하지 않은 문의글 리스트
     */
    List<HelpMessage> getUnansweredMessages(int adminId);

    /**
     * 답변한 문의글 리스트 조회
     * @param userNo 사용자 번호
     * @return 답변한 문의글 리스트
     */
    List<HelpMessage> getAnsweredMessagesByUser(int userNo);


    /**
     * 답변 메시지 번호로 원본 문의글 조회
     * @param replyMessageNo 답변 메시지 번호
     * @return 원본 문의글 정보
     */
    HelpMessage getOriginalByReplyMessageNo(int replyMessageNo);

    /**
     * 문의글과 해당 문의글에 달린 답변을 함께 조회
     * @param messageNo 문의글 번호
     * @return 문의글 및 답변이 포함된 HelpMessage 객체
     */
    HelpMessage getMessageWithReply(int messageNo);

}
