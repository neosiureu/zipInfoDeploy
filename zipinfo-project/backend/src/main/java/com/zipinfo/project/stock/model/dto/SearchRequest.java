package com.zipinfo.project.stock.model.dto;

import java.util.Map;

import org.springframework.web.bind.annotation.RequestBody;

import lombok.Data;


/** React 측에서 SearchBar에 입력된 검색 조건 및 좌표 범위를 Controller에서 처리하기 위한 Dto
 * * @param coords : 전송받는 좌표. 좌표 범위 안에 있는 
	 * sw : 현재 요청한 map의 남서쪽 끝의 좌표
	 * ne : 현재 요청한 map의 북동쪽 끝의 좌표
	 * Lat : 위도
	 * Lng : 경도
	 * @param(필수X) searchKeyword : 매물이름을 찾을때 사용하는 검색 키워드
	 * @param(필수X) locationCode : 소속된 시군구 코드
	 * @param(필수X) stockType : 판매 유형(매매 : 0, 전세 : 1, 월세 : 2)
	 * @param(필수X) stockForm : 매물 형태(아파트:1, 빌라:2, 오피스텔:3)
	 *
 */
@Data
public class SearchRequest {
	private Map<String, Double> coords; 
	private String searchKeyWord ; // 검색바 param(필수아님)
	private Integer locationCode; // 검색바 param(필수아님)
	private Integer stockType;// 검색바 param(필수아님)
	private Integer stockForm;// 검색바 param(필수아님)
}
