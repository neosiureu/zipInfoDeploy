package com.zipinfo.project.admin.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.admin.model.dto.HelpMessage;

@Mapper
public interface HelpMessageMapper {

    /**
     * 전체 문의 목록 조회
     * @return 문의 리스트
     */
    List<HelpMessage> selectAllMessages();

    /**
     * 특정 문의 상세 조회
     * @param messageNo 조회할 문의 번호
     * @return 문의 상세 정보 (없으면 null)
     */
    HelpMessage selectMessageById(@Param("messageNo") int messageNo);

    /**
     * 문의글에 답변 내용 업데이트
     * HELP_MESSAGE 테이블의 REPLY_CONTENT와 REPLY_DATE(SYSDATE) 컬럼 업데이트
     * 
     * @param messageNo 답변할 문의글 번호 (MESSAGE_NO)
     * @param replyContent 등록할 답변 내용
     * @return 업데이트 성공 시 1 이상의 값 반환, 실패 시 0 반환
     */
	int insertReply(HelpMessage message);
	
	/** 관리자가 문의내용 확인했을 경우 DB에 해당 문의글 READ_FL 'Y'로 변경
	 * @param messageNo
	 * @return
	 */
	int updateReadFlag(int messageNo);
	
	/** 답변 등록 시 해당 문의글 REPLY_YN = 'Y'로 변경
	 * @param messageNo
	 * @return
	 */
	int updateReplyYn(int messageNo);

	/** 관리자 보낸 메시지 제외
	 * @param adminId
	 * @return
	 */
	List<HelpMessage> selectUnansweredMessages(@Param("adminId") int adminId);
	
	
	/** [보낸 문의] → [문의 답변] 메뉴: REPLY_YN = 'Y' 인 문의글만 출력
	 * @param userNo
	 * @return
	 */
	List<HelpMessage> selectAnsweredMessagesByUser(int userNo);

	
	/** 기존 답변 문의 글 
	 * @param adminNo
	 * @param userNo
	 * @return
	 */
	List<HelpMessage> selectRepliesByAdminToUser(@Param("adminNo") int adminNo, @Param("userNo") int userNo);


	 /** 수정
	 * @param messageNo
	 * @param messageContent
	 * @return
	 */
	int updateReplyContent(@Param("messageNo") int messageNo,
             @Param("messageContent") String messageContent);

	/** 수정
	 * @param helpMessage
	 * @return
	 */
	int updateReply(HelpMessage helpMessage);

	/** 원 글 조회
	 * @param replyMessageNo
	 * @return
	 */
	HelpMessage selectOriginalByReplyMessageNo(@Param("replyMessageNo") int replyMessageNo);

}
