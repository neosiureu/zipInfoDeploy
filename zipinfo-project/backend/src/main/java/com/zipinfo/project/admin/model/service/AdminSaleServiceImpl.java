package com.zipinfo.project.admin.model.service;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    // 이미지 저장 경로 및 웹 접근 경로 설정 (application.properties에서 주입)
    @Value("${my.sale.folder-path}")
    private String saleFolderPath;

    @Value("${my.sale.web-path}")
    private String saleWebPath;

    /** 관리자 분양 매물 목록 조회
     *
     */
    @Override
    public List<Sale> selectSaleList() {
        return mapper.selectSaleList();
    }
    
	/** 관리자 분양 정보 등록
	 *
	 */
	@Override
	public int addSale(Sale sale) {
		 // 1. 매물 기본 정보 DB 저장 (자동 채번된 PK가 sale.saleStockNo 에 세팅됨)
       mapper.addSale(sale);

       // 2. 좌표 정보 저장
       Long saleNo = Long.valueOf(sale.getSaleStockNo());
       mapper.addSaleCoord(saleNo, sale.getLat(), sale.getLng());

       // 3. 생성된 PK 반환
       return sale.getSaleStockNo();
	}

	/** 관리자 분양 정보 이미지 등록
	 *
	 */
	@Override
	public void addSaleImages(int saleStockNo, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages) {
		Long saleNo = Long.valueOf(saleStockNo);

       // 썸네일(ORDER=1)
       saveImages(thumbnailImages, "thumbnail", saleNo, 1); // 기존에 이미지 저장하던 서비스단 메서드를 재활용하겠다

       // 평면도(ORDER=2)
       saveImages(floorImages, "floor", saleNo, 2);	// 기존에 이미지 저장하던 서비스단 메서드를 재활용하겠다	 
	}

	/** 관리자 분양 정보 수정
	 *
	 */
	@Override
   public void updateSaleImages(int saleStockNo,
                                List<MultipartFile> thumbnailImages,
                                List<MultipartFile> floorImages) {
       Long saleNo = Long.valueOf(saleStockNo);

       //  썸네일 이미지 덮어쓰기 order = 1
       if (thumbnailImages != null && !thumbnailImages.isEmpty()) {
           overwriteImages(thumbnailImages, "thumbnail", saleNo, 1);
       }

       // 평면도 이미지 덮어쓰기 order = 2
       if (floorImages != null && !floorImages.isEmpty()) {
           overwriteImages(floorImages, "floor", saleNo, 2);
       }
   }

    /** 관리자 분양 정보 등록 (기본 정보, 좌표, 이미지 포함)
     * @param sale 매물 정보
     * @param thumbnailImages 썸네일 이미지 리스트
     * @param floorImages 평면도 이미지 리스트
     */
    @Override
    public void addSale(Sale sale, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages) {

        // 1. 매물 기본 정보 DB 저장
        mapper.addSale(sale);
        Long saleNo = (long) sale.getSaleStockNo(); // 자동 채번된 매물 번호

        // 2. 좌표 정보 저장
        mapper.addSaleCoord(saleNo, sale.getLat(), sale.getLng());

        // 3. 썸네일 이미지 저장 및 DB 등록 (ORDER = 1)
        saveImages(thumbnailImages, "thumbnail", saleNo, 1);

        // 4. 평면도 이미지 저장 및 DB 등록 (ORDER = 2)
        saveImages(floorImages, "floor", saleNo, 2);
    }

    /** 관리자 분양 이미지 저장 및 DB 등록 (이미지 유형별 ORDER 고정)
     * @param images 이미지 리스트
     * @param subDir 하위 디렉토리 이름 (thumbnail or floor)
     * @param saleNo 매물 번호
     * @param order DB에 저장할 SALE_IMG_ORDER 값 (썸네일: 1, 평면도: 2)
     */
    private void saveImages(List<MultipartFile> images, String subDir, Long saleNo, int order) {
        if (images == null || images.isEmpty()) return;

        for (MultipartFile file : images) {
            if (!file.isEmpty()) {
                try {
                    String originalName = file.getOriginalFilename(); // 원본 파일명
                    String rename = Utility.fileRename(originalName); // 중복 방지를 위한 랜덤명

                    // 저장 디렉토리 생성
                    File dir = new File(saleFolderPath + "/" + subDir);
                    if (!dir.exists()) dir.mkdirs();

                    // 파일 저장
                    File dest = new File(dir, rename);
                    file.transferTo(dest);

                    // 웹 접근용 URL 생성
                    String imageUrl = saleWebPath + subDir + "/" + rename;

                    // 이미지 정보 DB에 저장 (모든 썸네일은 1, 평면도는 2로 고정)
                    mapper.addSaleImage(saleNo, imageUrl, order, originalName, rename);

                } catch (IOException e) {
                    log.error("[{} 이미지 저장 실패] {}", subDir, e.getMessage());
                    throw new RuntimeException(subDir + " 이미지 저장 중 오류 발생");
                }
            }
        }
    }

    /** 관리자 분양 정보 상세 조회 (기본 정보 + 이미지 목록 포함)
     *
     */
    @Override
    public Sale getSaleById(Long id) {
        Sale sale = mapper.selectSaleById(id);
        log.debug("[매물 상세] 기본 정보: {}", sale);
        log.debug("[매물 이미지 조회] Sale ID: {}", id);

        if (sale != null) {
            // 이미지 리스트 조회 후 DTO에 세팅
            List<Map<String, Object>> images = mapper.selectSaleImages(id);
            sale.setImageList(images);
            log.debug("[매물 이미지 조회 결과] {}", images);
        }
        return sale;
    }

    /** 관리자 분양 정보 수정 (기본 정보, 좌표, 이미지)
     * @param sale 수정할 매물 정보
     * @param thumbnailImages 썸네일 이미지
     * @param floorImages 평면도 이미지
     */
    @Override
    public void updateSale(Sale sale, List<MultipartFile> thumbnailImages, List<MultipartFile> floorImages) {
        Long saleNo = (long) sale.getSaleStockNo();

        // 1. 매물 정보 업데이트
        int result = mapper.updateSale(sale);
        if (result == 0) throw new RuntimeException("매물 정보 수정 실패");

        // 2. 좌표 업데이트
        mapper.updateSaleCoord(saleNo, sale.getLat(), sale.getLng());

        // 3. 이미지 덮어쓰기
        if (thumbnailImages != null && !thumbnailImages.isEmpty()) {
            overwriteImages(thumbnailImages, "thumbnail", saleNo, 1);
        }

        if (floorImages != null && !floorImages.isEmpty()) {
            overwriteImages(floorImages, "floor", saleNo, 2);
        }
    }

    /** 관리자 분양 정보 이미지 덮어쓰기 (기존 파일명 유지, 없으면 새로 등록)
     * @param newFiles 업로드된 이미지 리스트
     * @param subDir 저장 디렉토리명
     * @param saleNo 매물 번호
     * @param order SALE_IMG_ORDER (1: 썸네일, 2: 평면도)
     */
    private void overwriteImages(List<MultipartFile> newFiles, String subDir, Long saleNo, int order) {
        if (newFiles == null || newFiles.isEmpty()) return;

        List<String> existingRenames = mapper.selectImageRenamesByType(saleNo, subDir);

        for (int i = 0; i < newFiles.size(); i++) {
            MultipartFile file = newFiles.get(i);
            if (file.isEmpty()) continue;

            try {
                String originalName = file.getOriginalFilename();
                String rename;

                if (i < existingRenames.size()) {
                    // 기존 파일명으로 덮어쓰기
                    rename = existingRenames.get(i);
                } else {
                    // 새 파일로 추가
                    rename = Utility.fileRename(originalName);
                    String imageUrl = saleWebPath + subDir + "/" + rename;

                    // 새로운 이미지 DB 등록 (고정 order 사용)
                    mapper.addSaleImage(saleNo, imageUrl, order, originalName, rename);
                }

                // 파일 저장
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

    /** 분양 매물 삭제 (기본 정보 + 이미지 + 좌표 삭제 포함)
     *
     */
    @Override
    public void deleteSale(int id) throws Exception {
        // 1. 이미지 삭제
        mapper.deleteSaleImages(id);

        // 2. 좌표 삭제
        mapper.deleteSaleCoord(id);

        // 3. 기본 정보 삭제
        mapper.deleteSale(id);
    }
    
}
