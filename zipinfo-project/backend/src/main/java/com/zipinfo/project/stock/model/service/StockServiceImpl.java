package com.zipinfo.project.stock.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.common.searchbar.model.mapper.SearchBarMapper;
import com.zipinfo.project.stock.model.dto.SearchRequest;
import com.zipinfo.project.stock.model.dto.Stock;
import com.zipinfo.project.stock.model.mapper.StockMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class StockServiceImpl implements StockService{
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

	
	
}
