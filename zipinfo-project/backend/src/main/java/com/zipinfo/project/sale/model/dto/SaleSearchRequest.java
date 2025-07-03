package com.zipinfo.project.sale.model.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** React 측에서 SearchBar에 입력된 검색 조건 및 좌표 범위를 Controller에서 처리하기 위한 Dto
 * @param coords : 전송받는 좌표. 좌표 범위 안에 있는 
 * sw : 현재 요청한 map의 남서쪽 끝의 좌표
 * ne : 현재 요청한 map의 북동쪽 끝의 좌표
 * Lat : 위도
 * Lng : 경도
 * @param(필수X) searchKeyword : 매물이름을 찾을때 사용하는 검색 키워드
 * @param(필수X) locationCode : 소속된 시군구 코드
 * @param(필수X) saleStatus : 판매 유형(분양예정 : 1, 분양중 : 2, 분양완료 : 3)
 * @param(필수X) saleStockType : 판매 유형(아파트 : 1, 주택/빌라 : 2, 오피스텔 : 3)
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SaleSearchRequest {
	
	private Map<String, Double> coords; 
	private String searchKeyWord ; 		// 검색바 param(필수아님)
	private Integer locationCode; 		// 검색바 param(필수아님)
	private Integer saleStatus; 		// 검색바 param(필수아님)
	private Integer saleType;			// 검색바 param(필수아님)
	
}
