package com.zipinfo.project.admin.model.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.admin.model.dto.Advertisement;

public interface AdvertisementService {
    // 파일 저장: 업로드된 파일을 서버에 저장하고 저장 경로를 반환
    int saveFile(MultipartFile file, int memberNo);

	List<Advertisement> getAdList();

	int updateMain(int adNo);

	int deleteAd(int adNo);
}
