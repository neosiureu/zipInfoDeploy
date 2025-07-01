package com.zipinfo.project.admin.controller;

import java.util.List;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.admin.model.service.AdminSaleService;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.sale.model.dto.Sale;

@RestController
@RequestMapping("admin")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AdminSaleController {

    @Autowired
    private AdminSaleService service;

    /** 관리자 분양 정보 전체 조회
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

    /** 관리자 분양 정보 등록 (로그인된 관리자만 가능)
     * @param session
     * @param sale
     * @param thumbnailImages
     * @param floorImages
     * @return
     */
    @PostMapping(value = "/addSale", consumes = "multipart/form-data")
    public ResponseEntity<String> addSale(
        HttpSession session,
        @RequestPart("saleData") Sale sale,
        @RequestPart(value = "thumbnailImages", required = false) List<MultipartFile> thumbnailImages,
        @RequestPart(value = "floorImages", required = false) List<MultipartFile> floorImages
    ) {
        // 관리자 로그인 여부 확인
        Member loginMember = (Member) session.getAttribute("loginMember");

        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자 권한이 없습니다.");
        }

        try {
        	sale.setMemberNo(loginMember.getMemberNo()); // 관리자 회원 로그인 정보 추가
            service.addSale(sale, thumbnailImages, floorImages);
            return ResponseEntity.ok("등록 완료");
            
        } catch (Exception e) {
            e.printStackTrace();
            
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("매물 등록 중 오류 발생: " + e.getMessage());
        }
    }
    
    /** 관리자 매물 상세 조회(수정용)
     * @param id
     * @return
     */
    @GetMapping("/updateSale/{id}")
    public ResponseEntity<Sale> getSaleById(@PathVariable("id") Long id) {
        try {
            Sale sale = service.getSaleById(id);
            if (sale != null) {
                return ResponseEntity.ok(sale);
            } else {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /** 관리자 분양 정보 수정
     * @param session
     * @param id
     * @param sale
     * @param thumbnailImages
     * @param floorImages
     * @return
     */
    @PutMapping(value = "/updateSale/{id}", consumes = "multipart/form-data")
    public ResponseEntity<String> updateSale(
        HttpSession session,
        @PathVariable("id") Long id,
        @RequestPart("saleData") Sale sale,
        @RequestPart(value = "thumbnailImages", required = false) List<MultipartFile> thumbnailImages,
        @RequestPart(value = "floorImages", required = false) List<MultipartFile> floorImages
    ) {
        // 로그인 여부 및 권한 확인
        Member loginMember = (Member) session.getAttribute("loginMember");

        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자 권한이 없습니다.");
        }

        try {
        	sale.setSaleStockNo(id.intValue()); // 수정할 매물 번호 설정
            sale.setMemberNo(loginMember.getMemberNo()); // 로그인한 관리자 정보 설정

            service.updateSale(sale, thumbnailImages, floorImages);

            return ResponseEntity.ok("수정 완료");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("매물 수정 중 오류 발생: " + e.getMessage());
        }
    }
    
    /** 관리자 분양 정보 삭제
     * @param id
     * @param session
     * @return
     */
    @DeleteMapping("/deleteSale/{id}")
    public ResponseEntity<String> deleteSale(@PathVariable("id") Long id, HttpSession session) {
        Member loginMember = (Member) session.getAttribute("loginMember");

        if (loginMember == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("로그인이 필요합니다.");
        }

        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("관리자 권한이 없습니다.");
        }

        try {
            service.deleteSale(id.intValue());
            return ResponseEntity.ok("삭제 완료");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("삭제 중 오류 발생");
        }
    }

}
