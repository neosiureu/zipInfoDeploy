package com.zipinfo.project.stock.model.service;

import java.util.List;

import com.zipinfo.project.stock.model.dto.SearchRequest;
import com.zipinfo.project.stock.model.dto.Stock;

public interface StockService {

	List<Stock> getStockListInRange(SearchRequest sr);

}
