package com.zipinfo.project.common.searchbar.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.common.searchbar.model.dto.Sigungu;
@Mapper
public interface SearchBarMapper {

	List<Sigungu> selectSigungu(@Param("sido")int sido);
	
}
