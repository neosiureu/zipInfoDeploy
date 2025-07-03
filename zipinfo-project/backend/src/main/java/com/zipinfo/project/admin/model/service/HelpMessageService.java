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

	/** 관리자가 문의내용 확인했을 경우 DB에 해당 문의글 READ_FL 'Y'로 변경
	 * @param messageNo
	 * @return
	 */
	boolean markMessageAsRead(int messageNo);

	/** 답변 안한 문의 내용
	 * @param adminId
	 * @return
	 */
	List<HelpMessage> getUnansweredMessages(int adminId);

	/** 답변한 문의 내용
	 * @param userNo
	 * @return
	 */
	List<HelpMessage> getAnsweredMessagesByUser(int userNo);

	boolean updateReadFlag(int messageNo);

	/** 수정
	 * @param messageNo
	 * @param newContent
	 * @return
	 */
	boolean updateReplyContent(int messageNo, String newContent);

	/** 수정
	 * @param helpMessage
	 * @return
	 */
	int updateReply(HelpMessage helpMessage);

	/** 원글 조회
	 * @param replyMessageNo
	 * @return
	 */
	HelpMessage getOriginalByReplyMessageNo(int replyMessageNo);


	

	
}
