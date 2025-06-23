package com.zipinfo.project.admin.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.zipinfo.project.sale.model.dto.Sale;

@Mapper
public interface AdminSaleMapper {

	/** 분양 매물 조회 서비스
	 * @return
	 */
	List<Sale> selectSaleList();

}
