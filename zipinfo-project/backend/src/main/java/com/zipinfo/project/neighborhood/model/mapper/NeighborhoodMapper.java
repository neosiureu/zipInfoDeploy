package com.zipinfo.project.neighborhood.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.RowBounds;

import com.zipinfo.project.neighborhood.model.dto.Neighborhood;


@Mapper
public interface NeighborhoodMapper {

	int getListCount(int boardCode);

	List<Neighborhood> selectBoardList(int boardCode, RowBounds rowBounds);

	Neighborhood selectOne(Map<String, Integer> map);

	int updateReadCount(int boardNo);

	int selectReadCount(int boardNo);


	int deleteLike(Map<String, Object> map);

	int insertLike(Map<String, Object> map);

	int selectBoardLike(Object object);

}
