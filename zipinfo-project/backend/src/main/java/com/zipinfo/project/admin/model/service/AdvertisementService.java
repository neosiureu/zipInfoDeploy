package com.zipinfo.project.admin.model.service;

import org.springframework.web.multipart.MultipartFile;

public interface AdvertisementService {
    // 파일 저장: 업로드된 파일을 서버에 저장하고 저장 경로를 반환
    String saveFile(MultipartFile file);

    // 파일 삭제: 이미지 경로를 받아 해당 파일을 삭제
    boolean deleteFile(String filePath);
}
