package com.zipinfo.project.editneighborhood.model.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.zipinfo.project.neighborhood.model.dto.Neighborhood;

@Mapper
public interface EditNeighborhoodMapper {


	int boardInsert(Neighborhood inputBoard);

	int boardUpdate(Neighborhood inputBoard);

	int boardDelete(Neighborhood inputBoard);
	

}
