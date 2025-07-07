package com.zipinfo.project.admin.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.admin.model.dto.HelpMessage;

public interface HelpMessageMapper {

    /**
     * 전체 문의 목록 조회 (삭제되지 않은 글만)
     * @return 문의글 리스트
     */
    List<HelpMessage> selectAllMessages();

    /**
     * 단일 문의글 상세 조회 (첨부파일 및 작성자 닉네임 포함)
     * @param messageNo 조회할 문의글 번호
     * @return HelpMessage DTO
     */
    HelpMessage selectMessageById(int messageNo);

    /**
     * 답변 내용, 답변 여부, 수신자 번호 업데이트
     * HELP_MESSAGE 테이블 내 원본 문의글 행에 REPLY_CONTENT, REPLY_YN='Y', RECEIVER_NO 필드 수정
     * @param message 답변 내용과 대상 포함 DTO
     * @return 영향 받은 행 수
     */
    int updateReply(HelpMessage message);


    /**
     * 관리자가 답변하지 않은 문의글 조회
     * 본인(adminId)이 작성한 글 제외 (관리자 입장에서 미답변된 문의)
     * @param adminId 관리자 회원번호
     * @return 미답변 문의글 리스트
     */
    List<HelpMessage> selectUnansweredMessages(int adminId);


    /**
     * 답변 메시지 번호로 원본 문의글 조회
     * 답변은 별도 행이 아니므로 메시지 번호로 원본 행 조회
     * @param replyMessageNo 답변 메시지 번호
     * @return 원본 문의글 DTO
     */
    HelpMessage selectOriginalByReplyMessageNo(int replyMessageNo);

    /**
     * 관리자가 답변 완료한 문의글 목록 조회
     * adminId가 답변(작성자)인 문의글만 조회
     * @param adminId 관리자 회원번호
     * @return 관리자가 답변한 문의글 리스트
     */
    List<HelpMessage> selectRepliedMessagesByAdmin(int adminId);

	
}
