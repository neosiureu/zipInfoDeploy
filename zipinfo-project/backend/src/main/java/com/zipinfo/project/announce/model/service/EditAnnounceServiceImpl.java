package com.zipinfo.project.announce.model.service;

import org.springframework.stereotype.Service;

import com.zipinfo.project.announce.model.dto.Announce;
import com.zipinfo.project.announce.model.mapper.EditAnnounceMapper;

import lombok.RequiredArgsConstructor;

/**
 * EditAnnounceService 인터페이스 구현체
 * 공지사항 등록, 수정, 삭제, 조회 비즈니스 로직 처리 클래스
 */
@Service
@RequiredArgsConstructor
public class EditAnnounceServiceImpl implements EditAnnounceService {

    // EditAnnounceMapper 주입
    private final EditAnnounceMapper editAnnounceMapper;

    /**
     * 공지사항 등록 구현
     * 
     * @param announce 등록할 공지사항 DTO
     * @return 삽입된 행 수 (성공 시 1 이상)
     */
    @Override
    public int insertAnnounce(Announce announce) {
        return editAnnounceMapper.insertAnnounce(announce);
    }

    /**
     * 공지사항 수정 구현
     * 
     * @param announce 수정할 공지사항 DTO (announceNo 포함)
     * @return 수정된 행 수 (성공 시 1 이상)
     */
    @Override
    public int updateAnnounce(Announce announce) {
        return editAnnounceMapper.updateAnnounce(announce);
    }

    /**
     * 공지사항 삭제(논리 삭제) 구현
     * 
     * @param announceNo 삭제할 공지사항 번호
     * @return 수정된 행 수 (성공 시 1 이상)
     */
    @Override
    public int deleteAnnounce(int announceNo) {
        return editAnnounceMapper.deleteAnnounce(announceNo);
    }

    /**
     * 단일 공지사항 조회 구현
     * 
     * @param announceNo 조회할 공지사항 번호
     * @return 조회된 공지사항 DTO, 없으면 null
     */
    @Override
    public Announce selectOne(int announceNo) {
        return editAnnounceMapper.selectAnnounce(announceNo);
    }

    /**
     * 수정 기존 내용 불러오기
     */
    @Override
    public Announce selectAnnounceByNo(int announceNo) {
        return editAnnounceMapper.selectAnnounce(announceNo);
    }

}
