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
	public Map<String, Object> getSearchList(Map<String, Object> searchMap, int cp) {
		log.debug("서비스 임플에서 매퍼에 들어가기 직전에 찍힌 서치맵의 내용"+searchMap);
		// paramMap (key, query, boardCdoe)
			
			//1. 지정된 게시판 (boardCode)에서
			// 검색조건에 맞으면서
			// 삭제되지 않은 게시글 수를 조회

			int listCount = mapper.getSearchCount(searchMap);
			
			//2. 1번의 결과 + cp 를 이용해서
			// Pagination 객체를 생성
			Pagination pagination = new Pagination(cp, listCount);
			
			//3. 특정 게시판의 지정된 페이지 목록 조회
			int limit = pagination.getLimit(); // 10개씩 조회
		
		int offset = (cp - 1) * limit;
		
		RowBounds rowBounds = new RowBounds(offset,limit); 
		
		//mapper 메서드 호출 코드 수행
		// -> Mapper 메서드 호출 시 전달 할 수 있는 매개변수 1개
		// -> 2개를 전달할수 있는경우가있음
		// RowBounds 를 이용할 때
		// 1번쨰 : sql 에 전달할 파라미터
		// 2번째 : RowBounds 객체   (1번쨰 아무것도없으면 null 이래도 해야됨)
		List<Neighborhood> boardList = mapper.selectSearchList(searchMap,rowBounds);
			
			//4. 목록 조회 결과 + Pagination 객체를 Map으로 묶음
		Map<String, Object> map = new HashMap<>();
		map.put("pagination", pagination);
		map.put("boardList", boardList);
		
		return map;
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






	@Override
	public int like(Map<String, Object> map) {
		int result = 0;
		log.debug("좋아요 상태"+map.get("liked"));
		if ((Boolean) map.get("liked") == true) {
			result = mapper.deleteLike(map);
		} else {
			result = mapper.insertLike(map);
		}
		if (result > 0) {
			return mapper.selectBoardLike(map.get("boardNo"));
		}

		return -1;
	}







	





	
	
	
	

}
