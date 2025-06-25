package com.zipinfo.project.announce.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.announce.model.dto.Announce;

@Mapper
public interface EditAnnounceMapper {

    // 게시글 등록
    int insertBoard(Announce announce);

    // 게시글 수정
    int updateBoard(Announce announce);

    // 게시글 삭제 (논리 삭제)
    int deleteBoard(@Param("params") java.util.Map<String, Integer> params);

    // 게시글 상세 조회
    Announce selectBoard(@Param("announceNo") int announceNo);

    // 게시글 이미지 등록
    int insertBoardImage(@Param("announceNo") int announceNo, @Param("renamedFileName") String renamedFileName, @Param("order") int order);

    // 게시글 이미지 삭제 (order 기준)
    int deleteBoardImage(@Param("announceNo") int announceNo, @Param("order") int order);

    // 게시글 이미지 개수 조회 (announceNo, order 기준)
    int countImageByBoardNoAndOrder(@Param("announceNo") int announceNo, @Param("order") int order);

    // 게시글 이미지 수정 (order 기준)
    int updateBoardImage(@Param("announceNo") int announceNo, @Param("renamedFileName") String renamedFileName, @Param("order") int order);
}
