package com.zipinfo.project.admin.model.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import com.zipinfo.project.admin.model.mapper.HelpMessageMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class HelpMessageServiceImpl implements HelpMessageService {

    // MyBatis 매퍼 주입 (final + RequiredArgsConstructor로 생성자 자동 생성)
    private final HelpMessageMapper helpMessageMapper;

    /**
     * 전체 문의 목록 조회
     * 
     * @return 문의글 리스트
     */
    @Override
    public List<HelpMessage> getAllMessages() {
        return helpMessageMapper.selectAllMessages();
    }

    /**
     * 특정 문의 상세 조회
     * 
     * @param messageNo 조회할 문의글 번호
     * @return 문의 상세 정보 (없으면 null)
     */
    @Override
    public HelpMessage getHelpMessageById(int messageNo) {
        // 단순히 MESSAGE_NO로 문의글 한 건 조회
        // 필요 시 답변글 목록을 같이 조회해서 DTO에 포함하는 로직 확장 가능
        return helpMessageMapper.selectMessageById(messageNo);
    }

    /**
     * 문의글에 대한 답변 저장 (기존에는 문의글에 답변 내용만 업데이트하는 방식)
     * 
     * [변경 필요] 답변을 별도의 HelpMessage 레코드로 신규 등록하는 구조로 바꾸는 것을 권장합니다.
     * (답변도 하나의 문의글 형태로 분리되어 저장되고, INQUIRED_NO 컬럼으로 원본 문의글과 연결)
     * 
     * 현재 로직: 
     *  1) 해당 문의글이 존재하는지 확인
     *  2) 존재하면 문의글 테이블의 답변 내용과 답변일자를 UPDATE
     * 
     * 변경 후 로직 예시:
     *  1) 해당 문의글 존재 확인
     *  2) 답변 내용을 가진 새로운 HelpMessage 객체 생성 (INQUIRED_NO = 문의글 번호)
     *  3) 답변용 INSERT 수행
     * 
     * @param messageNo   답변할 문의글 번호
     * @param replyContent 등록할 답변 내용
     * @return 답변 저장 성공 여부
     */
    @Override
    @Transactional
    public boolean saveReply(HelpMessage message) {
        // 1) 문의글 존재 여부 확인
        HelpMessage messageResult = helpMessageMapper.selectMessageById(message.getInquiredNo());
        if (messageResult == null) {
            // 문의글이 없으면 답변 저장 불가
            return false;
        }
        
        // 현재는 문의글에 답변 내용과 답변일자를 UPDATE하는 기존 방식
        int result = helpMessageMapper.insertReply(message);

        

        return result > 0;
    }
}
