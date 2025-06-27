package com.zipinfo.project.announce.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.announce.model.dto.Announce;

@Mapper
public interface AnnounceMapper {

    /**
     * 공지사항 목록 조회 (기본 페이징)
     * @param cp 현재 페이지 번호 (1부터 시작)
     * @return 공지사항 리스트
     */
    List<Announce> selectAnnounceListWithSize(@Param("cp") int cp, @Param("size") int size);

    /**
     * 공지사항 단일 조회
     * @param announceNo 게시글 번호
     * @return 공지사항 객체
     */
    Announce selectOne(@Param("announceNo") int announceNo);

    /**
     * 공지사항 검색 결과 조회 (페이지 크기 지정)
     * @param key 검색 종류 (t: 제목, c: 내용, tc: 제목+내용, 기타: 작성자 닉네임)
     * @param query 검색어
     * @param cp 페이지 번호 (1부터 시작)
     * @param size 페이지당 게시글 수
     * @return 검색 결과 리스트
     */
    List<Announce> searchListWithSize(
    	    @Param("key") String key,
    	    @Param("query") String query,
    	    @Param("cp") int cp,
    	    @Param("size") int size
    	);
    
    /**
     * 공지사항 조회수 1 증가
     * @param announceNo 대상 게시글 번호
     * @return 수정된 행 수
     */
    int increaseViewCount(@Param("announceNo") int announceNo);

    /**
     * 전체 공지사항 개수 조회
     * @return 전체 게시글 수
     */
    int countAnnounce();

    /**
     * 검색 조건에 맞는 공지사항 개수 조회
     * @param key 검색 키
     * @param query 검색어
     * @return 검색된 게시글 수
     */
    int countSearchAnnounce(@Param("key") String key,
                            @Param("query") String query);
}
