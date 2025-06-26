package com.zipinfo.project.stock.model.mapper;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.stock.model.dto.CoordsStatInfo;
import com.zipinfo.project.stock.model.dto.Stock;

@Mapper
public interface StockMapper {

	List<Stock> selectStockInRange( @Param("swLat") double swLat, 
			@Param("swLng") double swLng, 
			@Param("neLat") double neLat, 
			@Param("neLng")double neLng,
			@Param("searchKeyWord") String searchKeyWord,
			@Param("locationCode") int locationCode,
			@Param("stockForm") int stockForm,
			@Param("stockType") int stockType);

	String selectSigunguFullName(@Param("code") int code);
	/** 매물들의 평균 좌표, 최소 lat 좌표, 최소 lng 좌표, 최대 lat 좌표, 최대 lng 좌표 반환
	 * @param swLat
	 * @param swLng
	 * @param neLat
	 * @param neLng
	 * @param searchKeyWord
	 * @param locationCode
	 * @param stockForm
	 * @param stockType
	 * @return
	 */
	CoordsStatInfo getCoordsFromStock(
			@Param("searchKeyWord") String searchKeyWord,
			@Param("locationCode") int locationCode,
			@Param("stockForm") int stockForm,
			@Param("stockType") int stockType);
	
	/** DB에 등록된 가장 최신 매물 4개(다른 조건 없음)를 가져옴
	 * @return
	 */
	List<Stock> selectAnyFour();
}
