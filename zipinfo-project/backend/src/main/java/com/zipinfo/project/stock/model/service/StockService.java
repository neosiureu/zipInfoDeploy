package com.zipinfo.project.stock.model.service;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.zipinfo.project.stock.model.dto.SearchRequest;
import com.zipinfo.project.stock.model.dto.Stock;

public interface StockService {

	List<Stock> getStockListInRange(SearchRequest sr);

	String getSigunguFullName(int code);

	
	
	
}
