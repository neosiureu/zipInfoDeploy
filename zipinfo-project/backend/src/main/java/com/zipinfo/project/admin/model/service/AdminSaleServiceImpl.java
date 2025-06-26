package com.zipinfo.project.admin.model.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
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

    /**
     * 관리자 분양 매물 목록 조회
     */
    @Override
    public List<Sale> selectSaleList() {
        return mapper.selectSaleList();
    }

    /** 관리자 분양 매물 등록 + 이미지 저장 처리 + 좌표 등록
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
}
