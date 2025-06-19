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
	
	/** 좌표 내부에 있는 매물들을 반환하는 함수.
	 *
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
				result.getSaleSatus(),
				result.getSaleType()
				);
		
	}
	
}
