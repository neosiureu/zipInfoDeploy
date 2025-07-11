package com.zipinfo.project.stock.model.service;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.common.searchbar.model.mapper.SearchBarMapper;
import com.zipinfo.project.stock.model.dto.CoordsStatInfo;
import com.zipinfo.project.stock.model.dto.SearchRequest;
import com.zipinfo.project.stock.model.dto.Stock;
import com.zipinfo.project.stock.model.mapper.StockMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class StockServiceImpl implements StockService {
	
	@Autowired
	private StockMapper mapper;
	
	@Autowired
	private SearchBarMapper searchBarMapper;
	
	/** 좌표 내부에 있는 매물들을 반환하는 함수.
	 *
	 */
	@Override
	public List<Stock> getStockListInRange(SearchRequest sr) {
		
		return mapper.selectStockInRange(
				sr.getCoords().get("swLat"),
				sr.getCoords().get("swLng"),
				sr.getCoords().get("neLat"),
				sr.getCoords().get("neLng"),
				sr.getSearchKeyWord(),
				sr.getLocationCode(),
				sr.getStockForm(),
				sr.getStockType()
				);
		
	}

	/** 시군구 코드를 입력하면 시군구의 이름을 반환하는 함수
	 * 11110 -> 종로구
	 *
	 */
	@Override
	public String getSigunguFullName(int code) {
		// TODO Auto-generated method stub
		return mapper.selectSigunguFullName(code);
	}
	
	/** 해당 조건들의 매물들의 평균 좌표, 최소 lat/lng, 최대 lat/lng를 반환하는 함수
	 * @return key : latCenter - 매물들의 평균 lat
	 * @return key : lngCenter - 매물들의 평균 lng
	 * @return key : latMin - 매물들중에서 가장 작은 lat값
	 * @return key : lngMin - 매물들중에서 가장 작은 lng값
	 * @return key : latMax - 매물들중에서 가장 큰 lat값
	 * @return key : lngMax - 매물들중에서 가장 큰 lng값
	 */
	@Override
	public CoordsStatInfo getCoordsFromStock(
			SearchRequest sr){
		
		return mapper.getCoordsFromStock(
				sr.getSearchKeyWord(),
				sr.getLocationCode(),
				sr.getStockForm(),
				sr.getStockType());
	}
	
	@Override
	public List<Stock> selectAnyFour(){
		return mapper.selectAnyFour();
	}

	@Override
	public Stock selectStockDetail(int stockNo) {
		return mapper.selectOneStock(stockNo);	
	}

	@Override
	public List<Map<String, Object>> stockChart() {
		return mapper.stockChart();
	}
	
}
