package com.zipinfo.project.common.searchbar.controller;



import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.zipinfo.project.common.searchbar.model.dto.Sigungu;
import com.zipinfo.project.common.searchbar.model.service.SearchBarService;

import lombok.extern.slf4j.Slf4j;

@Controller
@RestController
@CrossOrigin(origins = {
	    "http://localhost:5173",
	    "http://zipinfo.site",
	    "https://zipinfo.site", 
	    "http://www.zipinfo.site",
	    "https://www.zipinfo.site"
	})@RequestMapping("searchBar")
@Slf4j
public class SearchBarController { //author: 안준성
	
	@Autowired
	private SearchBarService service;
	@PostMapping("getAllSigungu")
	private ResponseEntity<?> getAllSigungu(@RequestBody  Map<String, String> request){
		
		Object rawValue = request.get("sidoSelected");
		int sido = Integer.parseInt(rawValue.toString());
		
		if(sido < 100) {
			 //return 
		    try {
		    	List<Sigungu> sigunguList = service.selectSigungu(sido);
				
				return ResponseEntity.status(HttpStatus.OK).body(sigunguList);
			}catch(Exception e) {
				log.error("시군구 DB 조회중 오류 발생", e);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("시군구 DB 조회중 오류 발생" + e.getMessage());
			
			}
		} else return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("잘못된 시도코드입니다.");
		
	}
}
