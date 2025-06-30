package com.zipinfo.project.neighborhood.controller;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.neighborhood.model.dto.Neighborhood;
import com.zipinfo.project.neighborhood.model.service.NeighborhoodService;
import com.zipinfo.project.util.Pagination;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

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

		 // 검색이 아닌 경우가 더 많으므로 이를 충족시키는지를 hasSearch라는 플래그를 통해 정한다.
		 boolean hasSearch =
		       (key    != null && !key.isBlank() // 제목 내용등이 비어서 넘어오면서 
		     && query  != null && !query.isBlank())   // + 검색 내용이 비어서 넘어온다면           
		    || (city    != null && !city.equals("-1"))           // 시.도가 선택되지 않은 채 넘어온다면
		    || (town    != null && !town.equals("-1"))           // 군.구.시가 선택되지 않은 채 넘어온다면
		    || (subject != null && !subject.equals("-1"));        // 주제(Q/R/E 중 하나가) 선택되지 않은 채 넘어온다면

		 // 위 조건 중 하나라도 만족한다면 검색이 아닌 것이다.
		
	    Map<String, Object> response = new HashMap<>();
	    
	
	    
	    if(hasSearch) {
	    	map = neighborhoodService.getSearchList(cp, key, query, city, town, subject);
	    	// 후에 구현하기로 한다.

	    }
	 // 위 조건 중 하나라도 만족한다면 검색이 아닌 것이다.
	    else {
	    	map = neighborhoodService.getBoardList(cp, key, query);
	    	// 일반적인 boardList 화면부터 구현한다.

	    }
	    
	    	    
	    
	    response.put("boardList", map.get("boardList"));
	    response.put("pagination", map.get("pagination"));
	     log.debug("프론트단으로 보낼 response 내용"+response);
	    return ResponseEntity.ok(response);
	}
	
	
	
	@GetMapping("/neighborhoodDetail")
	public ResponseEntity<Object> boardDetail(
			@SessionAttribute(value = "loginMember", required = false) Member loginMember,
			 @RequestParam("boardNo") int boardNo,
			HttpServletRequest req, 
			HttpServletResponse resp) {
		
		log.debug("디테일을 위한 컨트롤러단에 도달했습니다"+ boardNo+loginMember);
		int boardCode=1;


		Map<String, Integer> map = new HashMap<>();
		map.put("boardCode", boardCode);
		map.put("boardNo", boardNo);

		if (loginMember != null) {
			map.put("memberNo", loginMember.getMemberNo());
		}

		Neighborhood board = neighborhoodService.selectOne(map);
//		System.out.println(board);		// 목록 상세 조회 확인
		if(board == null) {
			return ResponseEntity.ok(null) ;

		} else {
/* --------------- 쿠키를 이용한 조회 수 증가 -------------------------*/
			// 비회원 또는 로그인한 회원의 글이 아닌 경우 ( == 글쓴이를 뺀 다른 사람)
			if(loginMember == null || 
					loginMember.getMemberNo() != board.getMemberNo()) {
				
				
				// 요청에 담겨있는 모든 쿠키 얻어오기
				Cookie[] cookies = req.getCookies();
				Cookie c = null;
				if(cookies!=null) {
					for(Cookie temp : cookies) {
						// 요청에 담긴 쿠키에 "readBoardNo" 가 존재 할 때
						if(temp.getName().equals("readBoardNo")) {
							c = temp;
							break;
						}
					}
				}
				
				int result = 0; // 조회수 증가 결과를 저장할 변수
				// "readBoardNo"가 쿠키에 없을 때
				if(c == null) {
					// 새 쿠키 생성("readBoardNo", [게시글번호])
					c = new Cookie("readBoardNo", "["+  boardNo + "]");
					result = neighborhoodService.updateReadCount(boardNo);
				} else {
				// 현재 글을 처음 읽는 경우
					if(c.getValue().indexOf("["+  boardNo + "]") == -1) {
						// 해당 글 번호를 쿠키에 누적 + 서비스 호출
						c.setValue(c.getValue() + "["+  boardNo + "]");
						// [2][30][400][2000][4000][2003]
						result = neighborhoodService.updateReadCount(boardNo);
					}
				}
				// 조회 수 증가 성공 / 조회 성공 시
				if(result > 0) {
					// 먼저 조회된 board의 readCount 값을
					// result 값을 다시 세팅
					board.setReadCount(result);
					// 쿠키 적용 경로 설정
					c.setPath("/");   // "/" 이하 경로 요청 시 쿠키 서버로 전달
					// 쿠키 수명 지정
					// 현재 시간을 얻어오기
					LocalDateTime now = LocalDateTime.now();
					// 다음날 자정  지정
					LocalDateTime nextDayMidnight = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
					// 다음날 자정까지 남은 시간 계산 (초 단위)
					long secondsUntilNextDay = Duration.between(now, nextDayMidnight).getSeconds();
					// 쿠키 수명 설정
					c.setMaxAge((int)secondsUntilNextDay);
					resp.addCookie(c); // 응답 객체를 이용해서 클라이언트에게 전달
				}
			}
/* --------------- 쿠키를 이용한 조회 수 증가 끝 -------------------------*/
			// 조회 결과가 있는 경우
			
		}
		
		log.info("최종적으로 보낼 것"+board);
		return ResponseEntity.ok(board);
	}
	

	
}
