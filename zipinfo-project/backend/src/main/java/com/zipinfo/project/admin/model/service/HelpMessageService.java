package com.zipinfo.project.admin.model.service;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import java.util.List;

public interface HelpMessageService {

    /**
     * 전체 문의 목록 조회
     * @return 문의글 리스트 (삭제되지 않은 글 포함)
     */
    List<HelpMessage> getAllMessages();

    /**
     * 단일 문의 조회
     * 첨부파일 URL은 별도로 세팅될 수 있음
     * @param messageNo 조회할 문의글 번호
     * @return 문의글 상세정보 (첨부파일 URL 포함 가능)
     */
    HelpMessage getMessageById(int messageNo);

    /**
     * 문의글과 해당 답변 함께 조회
     * DB상에서 답변이 별도로 저장된 경우에 사용 (현재는 답변이 한 행 내 컬럼으로 존재할 경우 필요없을 수 있음)
     * @param messageNo 문의글 번호
     * @return 문의글 및 답변이 포함된 HelpMessage 객체
     */
    HelpMessage getMessageWithReply(int messageNo);

    /**
     * 답변 등록 (REPLY_CONTENT, REPLY_YN, RECEIVER_NO 컬럼 업데이트)
     * @param message 답변 데이터가 포함된 HelpMessage 객체
     * @return 성공 시 true, 실패 시 false
     */
    boolean saveReply(HelpMessage message);

    /**
     * 관리자 기준 미답변 문의글 목록 조회
     * 본인이 작성한 글은 제외
     * @param adminId 관리자 회원번호
     * @return 미답변 문의글 리스트
     */
    List<HelpMessage> getUnansweredMessages(int adminId);


	/**
	 * 답변 메시지 번호로 원본 문의글 조회
	 * 첨부파일 URL 포함 세팅
	 * @param replyMessageNo 답변 메시지 번호
	 * @return 원본 문의글 DTO
	 */
	HelpMessage getOriginalByReplyMessageNo(int replyMessageNo);

	List<HelpMessage> getRepliedMessagesByAdmin(int adminId);
}
