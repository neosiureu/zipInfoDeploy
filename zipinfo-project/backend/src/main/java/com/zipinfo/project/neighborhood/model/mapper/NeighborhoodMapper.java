package com.zipinfo.project.neighborhood.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.session.RowBounds;

import com.zipinfo.project.neighborhood.model.dto.Neighborhood;


@Mapper
public interface NeighborhoodMapper {

	int getListCount(int boardCode);

	List<Neighborhood> selectBoardList(int boardCode, RowBounds rowBounds);

}
