package com.zipinfo.project.admin.model.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.admin.model.mapper.AdminSaleMapper;
import com.zipinfo.project.common.utility.Utility;
import com.zipinfo.project.sale.model.dto.Sale;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
@Slf4j
public class AdminSaleServiceImpl implements AdminSaleService {

    private final AdminSaleMapper mapper;

    // application.properties에서 설정한 분양 이미지 저장 경로 및 웹 접근 경로
    @Value("${my.sale.folder-path}")
    private String saleFolderPath;

    @Value("${my.sale.web-path}")
    private String saleWebPath;

    /** 관리자 분양 정보 목록 조회
     * 
     */
    @Override
    public List<Sale> selectSaleList() {
        return mapper.selectSaleList();
    }

    /** 관리자 분양 정보 등록 + 이미지 저장 처리 + 좌표 등록
     * @param sale 등록할 매물 정보 DTO
     * @param thumbnailImages 썸네일 이미지 파일 목록
     * @param floorImages 평면도 이미지 파일 목록
     */
    @Override
    public void addSale(Sale sale, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages) {

        // 1. DB에 매물 정보 INSERT (매물 번호는 자동 생성됨)
        mapper.addSale(sale);
        Long saleNo = (long) sale.getSaleStockNo(); // 등록 후 자동 채번된 PK

        // 2. SALE_COORD 테이블에 좌표 저장
        mapper.addSaleCoord(saleNo, sale.getLat(), sale.getLng());

        // 3. 썸네일 이미지 처리
        saveImages(thumbnailImages, "thumbnail", saleNo);

        // 4. 평면도 이미지 처리
        saveImages(floorImages, "floor", saleNo);
    }

    /** 이미지 목록을 지정된 디렉토리에 저장하고 DB에 등록
     * @param images 업로드된 이미지 파일 리스트
     * @param subDir 저장될 하위 디렉토리 (thumbnail 또는 floor)
     * @param saleNo 해당 이미지가 속한 매물 번호
     */
    private void saveImages(List<MultipartFile> images, String subDir, Long saleNo) {
        if (images == null || images.isEmpty()) return;

        int order = 1; // 이미지 순서
        for (MultipartFile file : images) {
            if (!file.isEmpty()) {
                try {
                    String originalName = file.getOriginalFilename();
                    String rename = Utility.fileRename(originalName);

                    // 1. 디렉토리 생성
                    File dir = new File(saleFolderPath + "/" + subDir);
                    if (!dir.exists()) dir.mkdirs();

                    // 2. 파일 저장
                    File dest = new File(dir, rename);
                    file.transferTo(dest);

                    // 3. 웹 접근 경로
                    String imageUrl = saleWebPath + subDir + "/" + rename;

                    // 4. DB INSERT
                    mapper.addSaleImage(saleNo, imageUrl, order, originalName, rename);
                    order++;

                } catch (IOException e) {
                    log.error("[{} 이미지 저장 실패] {}", subDir, e.getMessage());
                    throw new RuntimeException(subDir + " 이미지 저장 중 오류 발생");
                }
            }
        }
    }
    
    /** 관리자 매물 상세 조회(수정용) 서비스
     * 
     */
    @Override
    public Sale getSaleById(Long id) {
        Sale sale = mapper.selectSaleById(id);
        log.debug("[매물 상세] 기본 정보: {}", sale);
        log.debug("[매물 이미지 조회] Sale ID: {}", id);
        
        if (sale != null) {
            List<Map<String, Object>> images = mapper.selectSaleImages(id);
            sale.setImageList(images); // Sale DTO에 이미지 리스트 필드 추가 필요
            log.debug("[매물 이미지 조회 결과] {}", images);
        }
        return sale;
    }
    
    /** 관리자 분양 정보 수정 서비스
     *
     */
    @Override
    public void updateSale(Sale sale, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages) {
        Long saleNo = (long) sale.getSaleStockNo();

        int result = mapper.updateSale(sale);
        if (result == 0) throw new RuntimeException("매물 정보 수정 실패");

        mapper.updateSaleCoord(saleNo, sale.getLat(), sale.getLng());

        if (thumbnailImages != null && !thumbnailImages.isEmpty()) {
            overwriteImages(thumbnailImages, "thumbnail", saleNo);
        }

        if (floorImages != null && !floorImages.isEmpty()) {
            overwriteImages(floorImages, "floor", saleNo);
        }
    }

    /** 관리자 분양 정보 이미지 수정 서비스
     * @param newFiles
     * @param subDir
     * @param saleNo
     */
    private void overwriteImages(List<MultipartFile> newFiles, String subDir, Long saleNo) {
        if (newFiles == null || newFiles.isEmpty()) return;

        List<String> existingRenames = mapper.selectImageRenamesByType(saleNo, subDir);

        for (int i = 0; i < newFiles.size(); i++) {
            MultipartFile file = newFiles.get(i);
            if (file.isEmpty()) continue;

            try {
                String originalName = file.getOriginalFilename();
                String rename;

                if (i < existingRenames.size()) {
                    rename = existingRenames.get(i);
                } else {
                    rename = Utility.fileRename(originalName);
                    String imageUrl = saleWebPath + subDir + "/" + rename;
                    mapper.addSaleImage(saleNo, imageUrl, i + 1, originalName, rename);
                }

                File dir = new File(saleFolderPath + "/" + subDir);
                if (!dir.exists()) dir.mkdirs();

                File dest = new File(dir, rename);
                file.transferTo(dest);

            } catch (IOException e) {
                log.error("[{} 이미지 덮어쓰기 실패] {}", subDir, e.getMessage());
                throw new RuntimeException(subDir + " 이미지 덮어쓰기 중 오류 발생");
            }
        }
    }
   
}
