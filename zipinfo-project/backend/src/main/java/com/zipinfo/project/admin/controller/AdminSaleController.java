package com.zipinfo.project.admin.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.zipinfo.project.admin.model.service.AdminSaleService;
import com.zipinfo.project.sale.model.dto.Sale;

@RestController
@RequestMapping("admin")
@CrossOrigin(origins = "http://localhost:5173")
public class AdminSaleController {

    @Autowired
    private AdminSaleService service;
    
    @GetMapping("selectSaleList")
    public ResponseEntity<?> selectSaleList() {
    	
        try {
            List<Sale> saleList = service.selectSaleList();
            return ResponseEntity.ok(saleList);
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("매물 조회 중 오류가 발생했습니다: " + e.getMessage());
        }
        
    }


//    @PostMapping("/addSale")
//    public ResponseEntity<String> addSale(@RequestBody Sale sale) {
//        try {
//            service.addSale(sale);  // SaleService에서 DB 저장 로직 수행
//            return ResponseEntity.ok("매물이 등록되었습니다.");
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body("매물 등록 중 오류 발생: " + e.getMessage());
//        }
//    }
}
