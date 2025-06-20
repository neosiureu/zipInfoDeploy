package com.zipinfo.project.board.model.service;

import java.util.Map;

import com.zipinfo.project.board.model.dto.Board;

public interface EditAnnounceService {
    int boardInsert(Board board);

    int boardUpdate(Board board);

    int boardDelete(Map<String, Integer> params);

    Board selectBoard(int boardNo);
}
