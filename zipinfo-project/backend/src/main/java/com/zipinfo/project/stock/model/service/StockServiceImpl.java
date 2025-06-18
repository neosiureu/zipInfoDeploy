package com.zipinfo.project.stock.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.stock.model.dto.Stock;
import com.zipinfo.project.stock.model.mapper.StockMapper;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class StockServiceImpl implements StockService{
	@Autowired
	private StockMapper mapper;
	
	/** 좌표 내부에 있는 매물들을 반환하는 함수.
	 *
	 */
	@Override
	public List<Stock> getStockListInRange(double swLat, double swLng, double neLat, double neLng) {
		
		return mapper.selectStockInRange(swLat,swLng,neLat,neLng);
		
	}
	
}
