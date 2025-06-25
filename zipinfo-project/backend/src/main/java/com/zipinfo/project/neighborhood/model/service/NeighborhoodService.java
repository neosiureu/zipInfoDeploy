package com.zipinfo.project.neighborhood.model.service;

import java.util.Map;

import com.zipinfo.project.util.Pagination;

public interface NeighborhoodService {

	Map<String, Object> getBoardList(int cp, String key, String query);


	Map<String, Object> getSearchList(int cp, String key, String query, String city, String town, String subject);

}
