package com.zipinfo.project.stock.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.stock.service.StockService;

import lombok.extern.slf4j.Slf4j;
@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("stock")
@Slf4j
public class StockController {
	//@Autowired
	//private StockService service;
	
	
	/**
	 * @param coords : 전송받는 좌표. 좌표 범위 안에 있는 
	 * @return
	 */
	@PostMapping("selectItems")
	private ResponseEntity<?> selectItem(@RequestBody Map<String, Double> coords){
		
	    double swLat = coords.get("swLat");
	    double swLng = coords.get("swLng");
	    double neLat = coords.get("neLat");
	    double neLng = coords.get("neLng");

	    System.out.println("SW: " + swLat + ", " + swLng);
	    System.out.println("NE: " + neLat + ", " + neLng);

	    return ResponseEntity.ok("OK");
	}
}
