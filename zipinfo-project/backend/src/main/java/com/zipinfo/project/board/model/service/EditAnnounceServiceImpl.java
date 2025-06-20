package com.zipinfo.project.board.model.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.zipinfo.project.board.model.dto.Board;
import com.zipinfo.project.board.model.mapper.EditAnnounceMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EditAnnounceServiceImpl implements EditAnnounceService {

    private final EditAnnounceMapper boardMapper;

    @Override
    public int boardInsert(Board board) {
        return boardMapper.insertBoard(board);
    }

    @Override
    public int boardUpdate(Board board) {
        return boardMapper.updateBoard(board);
    }

    @Override
    public int boardDelete(Map<String, Integer> params) {
        return boardMapper.deleteBoard(params);
    }

    @Override
    public Board selectBoard(int boardNo) {
        return boardMapper.selectBoard(boardNo);
    }
}
