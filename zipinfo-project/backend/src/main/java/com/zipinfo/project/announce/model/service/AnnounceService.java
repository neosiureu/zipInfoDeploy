package com.zipinfo.project.announce.model.service;

import java.util.List;
import com.zipinfo.project.announce.model.dto.Announce;

/**
 * 공지사항 게시판 관련 비즈니스 로직 정의
 */
public interface AnnounceService {

    /**
     * 공지사항 목록 조회 (페이징 및 크기 지정)
     * 
     * @param cp   현재 페이지 번호 (1부터 시작)
     * @param size 페이지당 게시글 수
     * @return 게시글 목록
     */
    List<Announce> selectAnnounceList(int cp, int size);

    /**
     * 검색 조건에 따른 게시글 목록 조회 (페이징 및 크기 지정)
     * 
     * @param key   검색 조건 (t: 제목, c: 내용, tc: 제목+내용, 그 외: 작성자)
     * @param query 검색어
     * @param cp    현재 페이지 번호
     * @param size  페이지당 게시글 수
     * @return 검색된 게시글 목록
     */
    List<Announce> searchList(String key, String query, int cp, int size);

    /**
     * 게시글 상세 조회
     * 
     * @param announceNo 게시글 번호
     * @return 조회된 게시글
     */
    Announce selectOne(int announceNo);

    /**
     * 전체 게시글 수 조회 (페이징 계산용)
     * 
     * @return 전체 게시글 수
     */
    int countAnnounce();

    /**
     * 검색 조건에 해당하는 게시글 수 조회
     * 
     * @param key   검색 조건
     * @param query 검색어
     * @return 검색된 게시글 수
     */
    int countSearchAnnounce(String key, String query);

    /**
     * 게시글 조회수 증가
     * 
     * @param announceNo 대상 게시글 번호
     * @return 반영된 행 수
     */
    int increaseViewCount(int announceNo);

}
