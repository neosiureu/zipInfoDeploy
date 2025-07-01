package com.zipinfo.project.sale.model.service;

import java.util.List;

import com.zipinfo.project.sale.model.dto.Sale;
import com.zipinfo.project.sale.model.dto.SaleSearchRequest;

public interface SaleService {

	/** 좌표와 함께 분양 매물 조회 서비스
	 * @param result
	 * @return
	 */
	List<Sale> getSaleListInRange(SaleSearchRequest result);

	/** 단일 분양 매물 조회 서비스
	 * @param saleStockNo
	 * @return
	 */
	Sale selectSaleDetail(int saleStockNo);

	/** 분양 정보 전체 조회
	 * @return
	 */
	List<Sale> selectSaleList();
	
}
