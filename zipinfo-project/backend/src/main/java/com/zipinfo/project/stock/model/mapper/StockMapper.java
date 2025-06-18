package com.zipinfo.project.stock.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.stock.model.dto.Stock;

@Mapper
public interface StockMapper {

	List<Stock> selectStockInRange( @Param("swLat") double swLat, 
			@Param("swLng") double swLng, 
			@Param("neLat") double neLat, 
			@Param("neLng")double neLng);

}
