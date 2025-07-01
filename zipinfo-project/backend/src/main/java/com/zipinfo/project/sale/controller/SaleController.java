package com.zipinfo.project.sale.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.sale.model.dto.Sale;
import com.zipinfo.project.sale.model.dto.SaleSearchRequest;
import com.zipinfo.project.sale.model.service.SaleService;

import lombok.extern.slf4j.Slf4j;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("sale")
@Slf4j
public class SaleController {
	
	@Autowired
	private SaleService service;
	
	/** 좌표와 함께 분양 매물 조회
	 * @param coords : 전송받는 좌표값
	 * sw : 현재 요청한 map의 남서쪽 끝의 좌표
	 * ne : 현재 요청한 map의 북동쪽 끝의 좌표
	 * Lat : 위도
	 * Lng : 경도
	 * @param(필수X) searchKeyword : 매물이름을 찾을때 사용하는 검색 키워드
	 * @param(필수X) locationCode : 소속된 시군구 코드
	 * @param(필수X) saleSatus : 판매 유형(분양예정 : 1, 분양중 : 2, 분양완료 : 3)
	 * @param(필수X) saleStockType : 판매 유형(아파트 : 1, 주택/빌라 : 2, 오피스텔 : 3)
	 * @return
	 */
	@PostMapping("selectSaleMap")
	private ResponseEntity<?> selectSaleMap(@RequestBody SaleSearchRequest Result){
		
	    double swLat = Result.getCoords().get("swLat");
	    double swLng = Result.getCoords().get("swLng");
	    double neLat = Result.getCoords().get("neLat");
	    double neLng = Result.getCoords().get("neLng");
	    
	    System.out.println("=============================");
	    System.out.println("SW: " + swLat + ", " + swLng);
	    System.out.println("NE: " + neLat + ", " + neLng);
	    
	    // 요청 좌표 안쪽 범위 내부에 있는 모든 매물들을 불러오는 service 동작 
	    try {
	    	List<Sale> stockList = service.getSaleListInRange(Result);
			return ResponseEntity.status(HttpStatus.OK).body(stockList);
			
		}catch(Exception e) {
			log.error("매물 조회 중 오류 발생", e);
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("매물 조회 중 문제발생" + e.getMessage());
		
		}
	    
	}
	
	/** 분양 정보 전체 조회
     * @return
     */
    @GetMapping("selectSaleList")
    public ResponseEntity<List<Sale>> selectSaleList() {
        try {
            List<Sale> saleList = service.selectSaleList();
            return ResponseEntity.ok(saleList);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
	
	/** 단일 분양 매물 조회하기
	 * @param saleStockNo
	 * @return
	 */
	@GetMapping("/detail")
	public ResponseEntity<Sale> selectSaleDetail(@RequestParam("saleStockNo") int saleStockNo) {
	    try {
	    	
	        Sale saleDetail = service.selectSaleDetail(saleStockNo);
	        
	        if (saleDetail != null) {
	            return ResponseEntity.ok(saleDetail);
	            
	        } else {
	            return ResponseEntity.notFound().build();
	        }
	        
	    } catch (Exception e) {
	        e.printStackTrace();
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
	    }
	}
	
}
