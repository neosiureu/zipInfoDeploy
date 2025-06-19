package com.zipinfo.project.board.model.service;

import java.util.List;
import com.zipinfo.project.board.model.dto.Board;

public interface BoardService {
    List<Board> selectNoticeList();
    Board selectNotice(int boardNo);
    int insertNotice(Board notice);
    int updateNotice(Board notice);
    int deleteNotice(int boardNo);
}
