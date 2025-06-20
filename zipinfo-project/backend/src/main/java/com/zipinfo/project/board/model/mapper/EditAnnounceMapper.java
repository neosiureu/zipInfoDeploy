package com.zipinfo.project.board.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.board.model.dto.Board;

@Mapper
public interface EditAnnounceMapper {

    // 게시글 등록
    int insertBoard(Board board);

    // 게시글 수정
    int updateBoard(Board board);

    // 게시글 삭제 (논리 삭제)
    int deleteBoard(@Param("params") java.util.Map<String, Integer> params);

    // 게시글 상세 조회
    Board selectBoard(int boardNo);

    // 게시글 이미지 등록
    int insertBoardImage(@Param("boardNo") int boardNo, @Param("renamedFileName") String renamedFileName, @Param("order") int order);

    // 게시글 이미지 삭제 (order 기준)
    int deleteBoardImage(@Param("boardNo") int boardNo, @Param("order") int order);

    // 게시글 이미지 개수 조회 (boardNo, order 기준)
    int countImageByBoardNoAndOrder(@Param("boardNo") int boardNo, @Param("order") int order);

    // 게시글 이미지 수정 (order 기준)
    int updateBoardImage(@Param("boardNo") int boardNo, @Param("renamedFileName") String renamedFileName, @Param("order") int order);
}
