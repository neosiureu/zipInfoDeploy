package com.zipinfo.project.admin.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import com.zipinfo.project.admin.model.service.AdminSaleService;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.sale.model.dto.Sale;

@RestController
@RequestMapping("admin")
@CrossOrigin(origins = "http://localhost:5173", allowCredentials = "true")
public class AdminSaleController {

	/*
	 @PostMapping(value = "/addSale", consumes = "multipart/form-data")
public ResponseEntity<String> addSale(
    HttpSession session,  // 세션 사용
    @RequestPart("saleData") Sale sale,
    @RequestPart(value = "thumbnailImages", required = false) List<MultipartFile> thumbnailImages,
    @RequestPart(value = "floorImages", required = false) List<MultipartFile> floorImages
) {
    Member loginMember = (Member) session.getAttribute("loginMember");
    // 한 번에 모든 데이터 처리
    service.addSale(sale, thumbnailImages, floorImages);
    return ResponseEntity.ok("등록 완료");
}
	 */
	
	@Autowired
	private AdminSaleService service;

	/**
	 * 관리자 분양 정보 전체 조회
	 * 
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

	@PostMapping(value = "/addSale", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<Integer> addSale(@AuthenticationPrincipal Member loginMember, @RequestBody Sale sale) {
		if (loginMember == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		// 관리자 권한 체크
		if (loginMember.getMemberAuth() != 0) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		sale.setMemberNo(loginMember.getMemberNo());
		// service.addSale는 insert 후 생성된 PK를 리턴하도록 바꿔주세요
		int saleStockNo = service.addSale(sale);
		return ResponseEntity.ok(saleStockNo);
	}

	/**
	 * 위 메서드서 받은 saleStockNo로 multipart 이미지만 업로드
	 */
	@PostMapping(value = "/addSaleImg/{saleStockNo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<String> addSaleImages(@AuthenticationPrincipal Member loginMember,
			@PathVariable("saleStockNo") int saleStockNo,
			@RequestPart(value = "thumbnailImages", required = false) List<MultipartFile> thumbnailImages,
			@RequestPart(value = "floorImages", required = false) List<MultipartFile> floorImages) {
		if (loginMember == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (loginMember.getMemberAuth() != 0) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		service.addSaleImages(saleStockNo, thumbnailImages, floorImages);
		return ResponseEntity.ok("이미지 업로드 완료");
	}

	/*
	 * HttpSession session,
	 * 
	 * @RequestPart("saleData") Sale sale, // 여기에는 json이 오기를 원함
	 * 
	 * @RequestPart(value = "thumbnailImages", required = false) List<MultipartFile>
	 * thumbnailImages, //여기에는 파일이 오기를 원함
	 * 
	 * @RequestPart(value = "floorImages", required = false) List<MultipartFile>
	 * floorImages //여기에도 파일이 오기를 원함
	 * 
	 * 원래는 이랬었는데 스프링이 @RequestPart("saleData") Sale sale 로 바로 파싱해 주는 JSON형식과
	 * axios/FormData가 실제 보내는 바운더리(boundary) 사이가 맞지 않았음
	 * 
	 * JWT의 무상태(Stateless) 특성 JWT는 완전한 무상태 인증 방식이기 때문에:
	 * 
	 * 서버가 사용자별 상태를 세션에 저장하지 않음 첫 번째 요청의 saleData를 서버 메모리에 보관할 수 없음 두 번째 요청에서 첫 번째
	 * 요청 정보를 참조할 방법이 없음
	 * 
	 * 해결 방안
	 * 
	 * 첫 번째 요청: 데이터베이스에 분양 정보를 저장하고 생성된 PK 반환 
	 * 두 번째 요청: 해당 PK를 사용하여 이미지 파일들을 연결
	 * 
	 * 
	 */

	/**
	 * 관리자 매물 상세 조회(수정용)
	 * 
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

	@PutMapping(value = "/updateSale/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> updateSaleJson(
        @AuthenticationPrincipal Member loginMember,
        @PathVariable("id") Long id,
        @RequestBody Sale sale
    ) {
        // 1) 로그인 체크
        if (loginMember == null) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("로그인이 필요합니다.");
        }
        // 2) 관리자 권한 체크 (memberAuth == 0 이어야 함)
        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body("관리자 권한이 없습니다.");
        }

        try {
            // 수정 대상 매물 번호 및 수정자 세팅
            sale.setSaleStockNo(id.intValue());
            sale.setMemberNo(loginMember.getMemberNo());

            // 기본 정보만 업데이트 (이미지는 null 처리)
            service.updateSale(sale, null, null);

            return ResponseEntity
                .ok("기본 정보 수정 완료");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("매물 수정 중 오류 발생: " + e.getMessage());
        }
    }

    /**
     * 2) 이미지 파일만 multipart/form-data로 업로드
     */
    @PostMapping(value = "/updateSaleImg/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> updateSaleImages(
        @AuthenticationPrincipal Member loginMember,
        @PathVariable("id") int id,
        @RequestPart(value = "thumbnailImages", required = false)
            List<MultipartFile> thumbnailImages,
        @RequestPart(value = "floorImages", required = false)
            List<MultipartFile> floorImages
    ) {
        // 1) 로그인 체크
        if (loginMember == null) {
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body("로그인이 필요합니다.");
        }
        // 2) 관리자 권한 체크
        if (loginMember.getMemberAuth() != 0) {
            return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body("관리자 권한이 없습니다.");
        }

        try {
            // 받은 이미지 파일들로만 서비스 호출
            service.updateSaleImages(id, thumbnailImages, floorImages);
            return ResponseEntity
                .ok("이미지 수정 완료");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("이미지 수정 중 오류 발생: " + e.getMessage());
        }
    }
	/**
	 * 관리자 분양 정보 삭제
	 * 
	 * @param id
	 * @return
	 */
	@DeleteMapping("/deleteSale/{id}")
	public ResponseEntity<String> deleteSale(@PathVariable("id") Long id, @AuthenticationPrincipal Member loginMember) {

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
