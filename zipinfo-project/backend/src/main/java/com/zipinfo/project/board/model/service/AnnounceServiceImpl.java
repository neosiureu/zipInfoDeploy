package com.zipinfo.project.board.model.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.zipinfo.project.board.model.dto.Board;
import com.zipinfo.project.board.model.mapper.AnnounceMapper;

import lombok.RequiredArgsConstructor;

/**
 * 공지사항 게시판 관련 비즈니스 로직 처리 서비스 구현체
 * - Mapper를 통해 DB 접근
 * - 게시글 목록 조회, 검색, 상세 조회, 등록, 수정, 삭제, 조회수 증가 등을 담당
 */
@Service
@RequiredArgsConstructor
public class AnnounceServiceImpl implements AnnounceService {

    private final AnnounceMapper boardMapper;

    @Override
    public List<Board> selectBoardList(String boardSubject, int cp) {
        return boardMapper.selectBoardList(boardSubject, cp);
    }

    @Override
    public List<Board> searchList(String boardSubject, String key, String query, int cp) {
        return boardMapper.searchList(boardSubject, key, query, cp);
    }

    @Override
    public Board selectOne(Map<String, Object> map) {
        return boardMapper.selectOne(map);
    }

    @Override
    public Board selectBoard(int boardNo) {
        Map<String, Object> param = Map.of("boardNo", boardNo);
        return boardMapper.selectOne(param);
    }

    @Override
    public int increaseViewCount(int boardNo) {
        return boardMapper.increaseViewCount(boardNo);
    }

    @Override
    public int insertBoard(Board board) {
        return boardMapper.insertBoard(board);
    }

    @Override
    public int updateBoard(Board board) {
        return boardMapper.updateBoard(board);
    }

    @Override
    public int deleteBoard(Map<String, Object> map) {
        return boardMapper.deleteBoard(map);
    }
}
