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
}
