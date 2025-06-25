package com.zipinfo.project.announce.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.announce.model.dto.Announce;

@Mapper
public interface AnnounceMapper {

    /**
     * 공지사항 게시판 목록 조회 (페이징 처리 가능)
     * @param cp 현재 페이지 번호
     * @return 게시글 리스트
     */
    List<Announce> selectAnnounceList(@Param("cp") int cp);

    /**
     * 검색 조건에 따른 게시글 목록 조회
     * @param key 검색 키워드 종류 (제목, 내용, 작성자 등)
     * @param query 검색어
     * @param cp 현재 페이지 번호
     * @return 검색된 게시글 리스트
     */
    List<Announce> searchList(
        @Param("key") String key,
        @Param("query") String query,
        @Param("cp") int cp
    );

    /**
     * 단일 게시글 조회
     * @param map 조건 파라미터 (예: announceNo)
     * @return 단일 게시글 DTO
     */
    Announce selectOne(Map<String, Object> map);

    /**
     * 게시글 등록
     * @param announce 등록할 게시글 DTO
     * @return DB에 반영된 행 수
     */
    int insertAnnounce(Announce announce);

    /**
     * 게시글 수정
     * @param announce 수정할 게시글 DTO
     * @return DB에 반영된 행 수
     */
    int updateAnnounce(Announce announce);

    /**
     * 게시글 삭제 (논리 삭제)
     * @param map 삭제 조건 (예: announceNo)
     * @return DB에 반영된 행 수
     */
    int deleteAnnounce(Map<String, Object> map);

    /**
     * 게시글 조회수 1 증가
     * @param announceNo 게시글 번호
     * @return DB에 반영된 행 수
     */
    int increaseViewCount(@Param("announceNo") int announceNo);
}
