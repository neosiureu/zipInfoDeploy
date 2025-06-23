package com.zipinfo.project.admin.model.service;

import java.util.List;

import com.zipinfo.project.sale.model.dto.Sale;

public interface AdminSaleService {

	/** 분양 매물 조회 서비스
	 * @return
	 */
	List<Sale> selectSaleList();

}
