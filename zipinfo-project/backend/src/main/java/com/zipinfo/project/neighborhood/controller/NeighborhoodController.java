package com.zipinfo.project.neighborhood.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.member.model.service.MemberService;
import com.zipinfo.project.neighborhood.model.dto.Neighborhood;
import com.zipinfo.project.neighborhood.model.service.NeighborhoodService;
import com.zipinfo.project.util.Pagination;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping("/board")
@RequiredArgsConstructor
public class NeighborhoodController {
	private final NeighborhoodService neighborhoodService;
	
	@GetMapping("neighborhoodList")
	public ResponseEntity<Map<String, Object>> getNeighborhoodList(@RequestParam(value = "cp", required = false, defaultValue = "1") int cp,
			 @RequestParam Map<String, String> paramMap) {
		
		 List<Neighborhood> boardList;
		 Pagination pagination;
		 Map<String, Object> map;
		 String key     = paramMap.get("key");       // t, c, tc, w 등의 검색 타입
		 String query   = paramMap.get("query");     // 검색어 자체
		 String city    = paramMap.get("city");      // 시/도 코드 (기본값 -1)
		 String town    = paramMap.get("town");      // 군/구/시 코드 (기본값 -1)
		 String subject = paramMap.get("subject");   // Q, R, E (기본값 "")

		 boolean hasSearch =
		       (key    != null && !key.isBlank()
		     && query  != null && !query.isBlank())              // 제목·내용 검색
		    || (city    != null && !city.equals("-1"))           // 시/도가 선택됨
		    || (town    != null && !town.equals("-1"))           // 군/구/시가 선택됨
		    || (subject != null && !subject.isBlank()
		                       && !subject.equals("-1"));        // 주제(Q/R/E) 선택

		
	    Map<String, Object> response = new HashMap<>();
	    
	
	    
	    if(hasSearch) {
	    	map = neighborhoodService.getSearchList(cp, key, query, city, town, subject);

	    }
	    else {
	    	map = neighborhoodService.getBoardList(cp, key, query);

	    }
	    
	    	    
	    
	    response.put("boardList", map.get("boardList"));
	    response.put("pagination", map.get("pagination"));
	    log.debug("프론트단으로 보낼 response 내용"+response);
	    return ResponseEntity.ok(response);
	}
	

}
