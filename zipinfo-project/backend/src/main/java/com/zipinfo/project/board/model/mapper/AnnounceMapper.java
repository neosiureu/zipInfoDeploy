package com.zipinfo.project.board.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.board.model.dto.Board;

@Mapper
public interface AnnounceMapper {

    /**
     * 공지사항 게시판 목록 조회 (페이징 처리 가능)
     * @param cp 현재 페이지 번호
     * @return 게시글 리스트
     */
    List<Board> selectBoardList(@Param("cp") int cp);

    /**
     * 검색 조건에 따른 게시글 목록 조회
     * @param key 검색 키워드 종류 (제목, 내용, 작성자 등)
     * @param query 검색어
     * @param cp 현재 페이지 번호
     * @return 검색된 게시글 리스트
     */
    List<Board> searchList(
        @Param("key") String key,
        @Param("query") String query,
        @Param("cp") int cp
    );

    /**
     * 단일 게시글 조회
     * @param map 조건 파라미터 (예: boardNo)
     * @return 단일 게시글 DTO
     */
    Board selectOne(Map<String, Object> map);

    /**
     * 게시글 등록
     * @param board 등록할 게시글 DTO
     * @return DB에 반영된 행 수
     */
    int insertBoard(Board board);

    /**
     * 게시글 수정
     * @param board 수정할 게시글 DTO
     * @return DB에 반영된 행 수
     */
    int updateBoard(Board board);

    /**
     * 게시글 삭제 (논리 삭제)
     * @param map 삭제 조건 (예: boardNo)
     * @return DB에 반영된 행 수
     */
    int deleteBoard(Map<String, Object> map);

    /**
     * 게시글 조회수 1 증가
     * @param boardNo 게시글 번호
     * @return DB에 반영된 행 수
     */
    int increaseViewCount(@Param("boardNo") int boardNo);
}
