package com.zipinfo.project.board.model.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.zipinfo.project.board.model.dto.Board;
import com.zipinfo.project.board.model.mapper.BoardMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BoardServiceImpl implements BoardService {

    private final BoardMapper boardMapper;

    @Override
    public List<Board> selectNoticeList() {
        return boardMapper.selectNoticeList();
    }

    @Override
    public Board selectNotice(int boardNo) {
        return boardMapper.selectNotice(boardNo);
    }

    @Override
    public int insertNotice(Board notice) {
        return boardMapper.insertNotice(notice);
    }

    @Override
    public int updateNotice(Board notice) {
        return boardMapper.updateNotice(notice);
    }

    @Override
    public int deleteNotice(int boardNo) {
        return boardMapper.deleteNotice(boardNo);
    }
}
