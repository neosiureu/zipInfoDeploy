package com.zipinfo.project.admin.model.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.zipinfo.project.sale.model.dto.Sale;

public interface AdminSaleService {

    /** 관리자 분양 정보 목록 조회
     * @return
     */
    List<Sale> selectSaleList();

	/** 관리자 매물 상세 조회(수정용) 서비스
	 * @param id
	 * @return
	 */
	Sale getSaleById(Long id);
	
	/** 관리자 분양 정보 등록
	 * @param sale
	 * @return
	 */
	int addSale(Sale sale);

	/** 관리자 분양 정보 이미지 등록
	 * @param saleStockNo
	 * @param thumbnailImages
	 * @param floorImages
	 */
	void addSaleImages(int saleStockNo, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages);
    
	/** 관리자 분양 정보 수정
	 * @param sale
	 * @param thumbnailImages
	 * @param floorImages
	 */
	void updateSale(Sale sale, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages);

	/** 관리자 분양 정보 이미지 수정
	 * @param id
	 * @param thumbnailImages
	 * @param floorImages
	 */
	void updateSaleImages(int id, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages);

	/** 관리자 분양 정보 삭제
	 * @param id
	 * @throws Exception
	 */
	void deleteSale(int id) throws Exception;

    /** 관리자 분양 정보 등록
     * @param sale
     * @param thumbnailImages
     * @param floorImages
     */
    void addSale(Sale sale, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages);
}
