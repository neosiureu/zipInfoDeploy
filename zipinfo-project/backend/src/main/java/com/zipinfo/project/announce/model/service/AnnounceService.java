package com.zipinfo.project.announce.model.service;

import java.util.List;
import com.zipinfo.project.announce.model.dto.Announce;

/**
 * 공지사항 게시판 관련 비즈니스 로직 인터페이스
 * - 공지사항 CRUD, 조회, 검색, 페이징 관련 메서드 정의
 */
public interface AnnounceService {

    /**
     * 공지사항 목록 조회 (페이징 처리)
     * 
     * @param cp   현재 페이지 번호 (1부터 시작)
     * @param size 한 페이지에 보여줄 게시글 개수
     * @return 현재 페이지에 해당하는 공지사항 리스트 반환
     */
    List<Announce> selectAnnounceList(int cp, int size);

    /**
     * 검색 조건과 검색어를 이용한 공지사항 목록 조회 (페이징 처리)
     * 
     * @param key   검색 조건 키워드
     *              - "t" : 제목 검색
     *              - "c" : 내용 검색
     *              - "tc": 제목 + 내용 검색
     *              - 기타 : 작성자 검색
     * @param query 검색어 문자열
     * @param cp    현재 페이지 번호
     * @param size  한 페이지에 보여줄 게시글 개수
     * @return 검색 조건에 맞는 공지사항 목록 반환
     */
    List<Announce> searchList(String key, String query, int cp, int size);

    /**
     * 특정 공지사항 게시글 상세 조회
     * 
     * @param announceNo 조회할 게시글의 고유 번호
     * @return 해당 게시글 정보 (없으면 null 반환 가능)
     */
    Announce selectOne(int announceNo);

    /**
     * 전체 공지사항 게시글 수 조회
     * - 페이징 처리를 위한 전체 게시글 개수 반환
     * 
     * @return 전체 공지사항 게시글 개수
     */
    int countAnnounce();

    /**
     * 검색 조건에 해당하는 게시글 수 조회
     * - 검색 결과에 대한 페이징 처리를 위한 게시글 개수 반환
     * 
     * @param key   검색 조건 키워드
     * @param query 검색어 문자열
     * @return 검색된 게시글 총 개수
     */
    int countSearchAnnounce(String key, String query);

    /**
     * 게시글 조회수 증가 처리
     * - 특정 게시글 조회 시 호출하여 조회수를 1 증가시킴
     * 
     * @param announceNo 조회수 증가 대상 게시글 번호
     * @return 조회수 증가가 반영된 행(row) 수 (보통 1)
     */
    int increaseViewCount(int announceNo);

    
}
