package com.zipinfo.project.sale.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.sale.model.dto.Sale;

@Mapper
public interface SaleMapper {

	/** 좌표와 함께 분양 매물 조회
	 * @param swLat
	 * @param swLng
	 * @param neLat
	 * @param neLng
	 * @param searchKeyWord
	 * @param locationCode
	 * @param saleStatus
	 * @param saleType
	 * @return
	 */
	List<Sale> selectSaleInRange(
			@Param("swLat") double swLat, 
			@Param("swLng") double swLng, 
			@Param("neLat") double neLat, 
			@Param("neLng") double neLng,
			@Param("searchKeyWord") String searchKeyWord,
			@Param("locationCode") int locationCode,
			@Param("saleStatus") int saleStatus,
			@Param("saleType") int saleType);

	/** 단일 분양 매물 조회
	 * @param saleStockNo
	 * @return
	 */
	Sale selectSaleDetail(int saleStockNo);
	
}
