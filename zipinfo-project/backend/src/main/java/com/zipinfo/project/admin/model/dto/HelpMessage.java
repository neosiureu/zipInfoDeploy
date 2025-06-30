package com.zipinfo.project.admin.model.dto;

import java.sql.Date;

import lombok.Data;

@Data
public class HelpMessage {

    // 문의 글 번호 (Primary Key)
    private int messageNo;

    // 문의 제목
    private String messageTitle;

    // 문의 내용
    private String messageContent;

    // 문의 작성일 (기본값: SYSDATE)
    private String messageWriteDate;

    // 문의 읽음 여부 ('Y' 또는 'N')
    private String messageReadFl;

    // 사용자 삭제 여부 ('Y' 또는 'N')
    private String userDelFl;

    // 관리자 삭제 여부 ('Y' 또는 'N')
    private String adminDelFl;

    // 문의 보낸 사람의 회원 번호 (FK: MEMBER 테이블)
    private int senderNo;

    // 문의 받는 사람의 회원 번호 (보통 관리자, FK: MEMBER 테이블)
    private int receiverNo;

    // 답변 내용 (답변이 등록된 경우에만 존재)
    private String replyContent;

    // 답변 등록 일자
    private String replyDate;

    // 답해줄 문의 번호
    private int inquiredNo;
}
