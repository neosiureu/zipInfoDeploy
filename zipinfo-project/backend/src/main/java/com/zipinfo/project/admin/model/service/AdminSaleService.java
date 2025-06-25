package com.zipinfo.project.admin.model.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import com.zipinfo.project.sale.model.dto.Sale;

public interface AdminSaleService {

    /** 관리자 분양 매물 목록 조회
     * @return
     */
    List<Sale> selectSaleList();

    /** 관리자 분양 매물 등록
     * @param sale
     * @param thumbnailImages
     * @param floorImages
     */
    void addSale(Sale sale, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages);
}
