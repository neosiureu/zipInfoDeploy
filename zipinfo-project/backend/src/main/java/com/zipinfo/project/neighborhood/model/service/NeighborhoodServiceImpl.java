package com.zipinfo.project.neighborhood.model.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.RowBounds;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.neighborhood.model.dto.Neighborhood;
import com.zipinfo.project.neighborhood.model.mapper.NeighborhoodMapper;
import com.zipinfo.project.util.Pagination;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional (rollbackFor = Exception.class)
public class NeighborhoodServiceImpl implements NeighborhoodService {
	
	private final NeighborhoodMapper mapper;
	int boardCode =1;
	

	@Override
	public Map<String, Object> getBoardList(int cp, String key, String query) {

		int listCount = mapper.getListCount(boardCode);
		System.out.println(listCount);

		Pagination pagination = new Pagination(cp, listCount);

		int limit = pagination.getLimit();
		int offset = (cp - 1) * limit;
		RowBounds rowBounds = new RowBounds(offset, limit);

		List<Neighborhood> boardList = mapper.selectBoardList(boardCode, rowBounds);
		System.out.println(boardList);

		Map<String, Object> map = new HashMap<>();

		map.put("pagination", pagination);
		map.put("boardList", boardList);

		return map;
	}


	

	
	@Override
	public Map<String, Object> getSearchList(int cp, String key, String query, String city, String town,
			String subject) {
		// TODO Auto-generated method stub
		return null;
	}





	@Override
	public Neighborhood selectOne(Map<String, Integer> map) {
		log.debug("디테일을 위한 서비스단에 도달했습니다."+ map);

		return mapper.selectOne(map);
	}





	@Override
	public int updateReadCount(int boardNo) {
		int result = mapper.updateReadCount(boardNo);
		if (result > 0) {
			return mapper.selectReadCount(boardNo);
		}
		return -1;
	}

	
	
	
	

}
