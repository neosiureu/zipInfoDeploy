package com.zipinfo.project.announce.model.service;

import com.zipinfo.project.announce.model.dto.Announce;

/**
 * 공지사항 등록, 수정, 삭제, 조회를 담당하는 서비스 인터페이스
 */
public interface EditAnnounceService {

    /**
     * 공지사항 등록
     * 
     * @param announce 등록할 공지사항 객체
     * @return 삽입된 행 수 (성공 시 1 이상)
     */
    int insertAnnounce(Announce announce);

    /**
     * 공지사항 수정
     * 
     * @param announce 수정할 공지사항 객체 (announceNo 포함)
     * @return 수정된 행 수 (성공 시 1 이상)
     */
    int updateAnnounce(Announce announce);

    /**
     * 공지사항 삭제 (논리 삭제)
     * 
     * @param announceNo 삭제할 공지사항 번호
     * @return 수정된 행 수 (성공 시 1 이상)
     */
    int deleteAnnounce(int announceNo);

    /**
     * 단일 공지사항 조회
     * 
     * @param announceNo 조회할 공지사항 번호
     * @return 조회된 공지사항 객체, 없으면 null
     */
    Announce selectOne(int announceNo);
}
