package com.zipinfo.project.board.model.service;

import java.util.List;
import java.util.Map;

import com.zipinfo.project.board.model.dto.Board;

/**
 * 공지사항 게시판 관련 서비스 인터페이스
 * - 게시글 목록 조회, 검색, 상세 조회, 등록, 수정, 삭제, 조회수 증가 기능을 정의
 */
public interface AnnounceService {

    /**
     * 게시글 목록 조회 (페이징 지원)
     * 
     * @param cp 현재 페이지 번호
     * @return 게시글 목록
     */
    List<Board> selectBoardList(int cp);

    /**
     * 게시글 검색 목록 조회 (페이징 및 필터링 포함)
     * 
     * @param key 검색키 (t: 제목, c: 내용, tc: 제목+내용, 그외: 작성자 닉네임)
     * @param query 검색어
     * @param cp 현재 페이지 번호
     * @return 검색된 게시글 목록
     */
    List<Board> searchList(String key, String query, int cp);

    /**
     * 게시글 상세 조회 (여러 조건을 Map으로 받음)
     * 
     * @param map 게시글 식별용 파라미터 (예: boardNo 등)
     * @return 조회된 게시글 DTO
     */
    Board selectOne(Map<String, Object> map);

    /**
     * 게시글 상세 조회 (게시글 번호로 단일 조회)
     * 
     * @param boardNo 게시글 번호
     * @return 조회된 게시글 DTO
     */
    Board selectBoard(int boardNo);

    /**
     * 게시글 조회수 증가
     * 
     * @param boardNo 조회수를 증가시킬 게시글 번호
     * @return 영향받은 행 수 (1 이상이면 성공)
     */
    int increaseViewCount(int boardNo);

    /**
     * 게시글 등록
     * 
     * @param board 등록할 게시글 DTO
     * @return 성공 시 영향받은 행 수
     */
    int insertBoard(Board board);

    /**
     * 게시글 수정
     * 
     * @param board 수정할 게시글 DTO
     * @return 성공 시 영향받은 행 수
     */
    int updateBoard(Board board);

    /**
     * 게시글 삭제 (논리 삭제)
     * 
     * @param map 삭제 조건 (예: boardNo)
     * @return 성공 시 영향받은 행 수
     */
    int deleteBoard(Map<String, Object> map);
}
