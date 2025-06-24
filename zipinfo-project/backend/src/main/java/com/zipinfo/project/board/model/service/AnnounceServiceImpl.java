package com.zipinfo.project.board.model.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.zipinfo.project.board.model.dto.Board;
import com.zipinfo.project.board.model.mapper.AnnounceMapper;

import lombok.RequiredArgsConstructor;

/**
 * 공지사항 게시판 관련 비즈니스 로직 처리 서비스 구현체
 * - MyBatis Mapper를 통해 DB 접근
 * - 게시글 목록 조회, 검색, 상세 조회, 등록, 수정, 삭제, 조회수 증가 기능을 처리
 */
@Service
@RequiredArgsConstructor
public class AnnounceServiceImpl implements AnnounceService {

    private final AnnounceMapper boardMapper;

    /**
     * 게시글 목록 조회 (페이징)
     * @param cp 현재 페이지 번호
     * @return 게시글 목록 리스트
     */
    @Override
    public List<Board> selectBoardList(int cp) {
        return boardMapper.selectBoardList(cp);
    }

    /**
     * 게시글 검색 목록 조회
     * @param key 검색 타입 (t: 제목, c: 내용, tc: 제목+내용, 기타: 작성자 닉네임 등)
     * @param query 검색어
     * @param cp 현재 페이지 번호
     * @return 검색 결과 게시글 리스트
     */
    @Override
    public List<Board> searchList(String key, String query, int cp) {
        return boardMapper.searchList(key, query, cp);
    }

    /**
     * 게시글 단일 조회 (다양한 조건 지원)
     * @param map 조회 조건 (예: boardNo)
     * @return 단일 게시글 DTO
     */
    @Override
    public Board selectOne(Map<String, Object> map) {
        return boardMapper.selectOne(map);
    }

    /**
     * 게시글 단일 조회 (boardNo 기준)
     * @param boardNo 게시글 번호
     * @return 단일 게시글 DTO
     */
    @Override
    public Board selectBoard(int boardNo) {
        Map<String, Object> param = Map.of("boardNo", boardNo);
        return boardMapper.selectOne(param);
    }

    /**
     * 게시글 조회수 1 증가
     * @param boardNo 조회수를 증가시킬 게시글 번호
     * @return 영향받은 행 수
     */
    @Override
    public int increaseViewCount(int boardNo) {
        return boardMapper.increaseViewCount(boardNo);
    }

    /**
     * 게시글 등록
     * @param board 등록할 게시글 DTO
     * @return 영향받은 행 수
     */
    @Override
    public int insertBoard(Board board) {
        return boardMapper.insertBoard(board);
    }

    /**
     * 게시글 수정
     * @param board 수정할 게시글 DTO
     * @return 영향받은 행 수
     */
    @Override
    public int updateBoard(Board board) {
        return boardMapper.updateBoard(board);
    }

    /**
     * 게시글 삭제 (논리 삭제)
     * @param map 삭제 조건 (예: boardNo)
     * @return 영향받은 행 수
     */
    @Override
    public int deleteBoard(Map<String, Object> map) {
        return boardMapper.deleteBoard(map);
    }
}
