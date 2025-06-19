package com.zipinfo.project.sale.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.stock.controller.StockController;
import com.zipinfo.project.stock.model.dto.Stock;
import com.zipinfo.project.stock.model.service.StockService;

import lombok.extern.slf4j.Slf4j;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("sale")
@Slf4j
public class SaleController {
	
	@Autowired
	private StockService service;
	
	/**
	 * @param coords : 전송받는 좌표. 좌표 범위 안에 있는 
	 * sw : 현재 요청한 map의 남서쪽 끝의 좌표
	 * ne : 현재 요청한 map의 북동쪽 끝의 좌표
	 * Lat : 위도
	 * Lng : 경도
	 * @return
	 */
	@PostMapping("selectItems")
	private ResponseEntity<?> selectItem(@RequestBody Map<String, Double> coords){
		
	    double swLat = coords.get("swLat");
	    double swLng = coords.get("swLng");
	    double neLat = coords.get("neLat");
	    double neLng = coords.get("neLng");
	    
	    System.out.println("=============================");
	    System.out.println("SW: " + swLat + ", " + swLng);
	    System.out.println("NE: " + neLat + ", " + neLng);
	    
	    //요청 좌표 안쪽 범위 내부에 있는 모든 매물들을 불러오는 service동작
	    
	    
	    //return 
	    try {
	    	List<Stock> stockList = service.getStockListInRange(swLat, swLng, neLat, neLng);
			return ResponseEntity.status(HttpStatus.OK).body(stockList);
			
		} catch(Exception e) {
			log.error("매물 조회 중 오류 발생", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("매물 조회 중 문제발생" + e.getMessage());
		
		}
	}
	
}
