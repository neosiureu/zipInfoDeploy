package com.zipinfo.project.board.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.board.model.dto.Board;

@Mapper
public interface AnnounceMapper {

    List<Board> selectBoardList(
        @Param("boardSubject") String boardSubject,
        @Param("cp") int cp
    );

    List<Board> searchList(
        @Param("boardSubject") String boardSubject,
        @Param("key") String key,
        @Param("query") String query,
        @Param("cp") int cp
    );

    Board selectOne(Map<String, Object> map);

    int insertBoard(Board board);

    int updateBoard(Board board);

    int deleteBoard(Map<String, Object> map);

    int increaseViewCount(@Param("boardNo") int boardNo);
}
