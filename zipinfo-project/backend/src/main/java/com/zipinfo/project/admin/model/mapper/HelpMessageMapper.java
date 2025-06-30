package com.zipinfo.project.admin.model.mapper;

import java.util.List;

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
}
