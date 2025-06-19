package com.zipinfo.project.board.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.board.model.dto.Board;

@Mapper
public interface BoardMapper {
    List<Board> selectNoticeList();

    Board selectNotice(int boardNo);

    int insertNotice(Board notice);

    int updateNotice(Board notice);

    int deleteNotice(int boardNo);
}
