package com.zipinfo.project.sale.model.service;

import java.util.List;

import com.zipinfo.project.sale.model.dto.Sale;
import com.zipinfo.project.sale.model.dto.SaleSearchRequest;

public interface SaleService {

	List<Sale> getSaleListInRange(SaleSearchRequest result);
	
}
