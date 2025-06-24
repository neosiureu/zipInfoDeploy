package com.zipinfo.project.sale.model.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.sale.model.dto.Sale;
import com.zipinfo.project.sale.model.dto.SaleSearchRequest;
import com.zipinfo.project.sale.model.mapper.SaleMapper;
import com.zipinfo.project.stock.model.dto.SearchRequest;

import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class SaleServiceImpl implements SaleService {
	
	@Autowired
	private SaleMapper mapper;
	
	/**
	 * 좌표와 함께 분양 매물 조회 서비스
	 */
	@Override
	public List<Sale> getSaleListInRange(SaleSearchRequest result) {
		
		return mapper.selectSaleInRange(
				result.getCoords().get("swLat"),
				result.getCoords().get("swLng"),
				result.getCoords().get("neLat"),
				result.getCoords().get("neLng"),
				result.getSearchKeyWord(),
				result.getLocationCode(),
				result.getSaleStatus(),
				result.getSaleType()
				);
		
	}
	
	/**
	 * 단일 분양 매물 조회 서비스
	 */
	@Override
	public Sale selectSaleDetail(int saleStockNo) {
		return mapper.selectSaleDetail(saleStockNo);
	}
}
