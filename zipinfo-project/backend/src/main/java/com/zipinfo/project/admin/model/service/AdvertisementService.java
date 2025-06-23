package com.zipinfo.project.admin.model.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;
import com.zipinfo.project.admin.model.dto.Advertisement;

public interface AdvertisementService {
    // 광고 등록(DB 저장 전 파일 저장 포함)
    Advertisement registerAd(Advertisement ad);

    // 광고 리스트 조회
    List<Advertisement> getAllAds();

    // 광고 수정
    Advertisement updateAd(Advertisement ad);

    // 광고 삭제
    boolean deleteAd(int adId);

    // 파일 저장 (업로드 파일 처리)
    String saveFile(MultipartFile file);
}
