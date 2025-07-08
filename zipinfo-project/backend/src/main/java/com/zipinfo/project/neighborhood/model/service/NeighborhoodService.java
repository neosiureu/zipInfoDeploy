package com.zipinfo.project.neighborhood.model.service;

import java.util.Map;

import com.zipinfo.project.neighborhood.model.dto.Neighborhood;
import com.zipinfo.project.util.Pagination;

public interface NeighborhoodService {

	Map<String, Object> getBoardList(int cp, String key, String query);


	Neighborhood selectOne(Map<String, Integer> map);


	int updateReadCount(int boardNo);


	int like(Map<String, Object> paramMap);


	Map<String, Object> getSearchList(Map<String, Object> searchMap, int cp);





}
