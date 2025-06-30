package com.zipinfo.project.admin.model.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdvertisementServiceImpl implements AdvertisementService {

    // 업로드 폴더 절대 경로 (윈도우 환경 기준)
    private final String uploadDir = "C:/uploadFiles/ad";

    /**
     * 서버에 파일 저장 후 클라이언트에서 접근 가능한 경로 문자열 반환
     * @param file - 업로드된 MultipartFile
     * @return 저장된 파일의 웹 접근 경로 (예: "/uploads/uuid_파일명.jpg")
     */
    @Override
    public String saveFile(MultipartFile file) {
        try {
            File uploadPath = new File(uploadDir);
            if (!uploadPath.exists()) uploadPath.mkdirs();

            String originalName = file.getOriginalFilename();
            if (originalName == null) throw new RuntimeException("파일명 없음");

            String uniqueFilename = UUID.randomUUID() + "_" + originalName;

            File dest = new File(uploadPath, uniqueFilename);

            file.transferTo(dest);

            return "/uploads/" + uniqueFilename;

        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("파일 저장 실패", e);
        }
    }

    /**
     * 서버에 저장된 파일 삭제
     * @param filePath - 클라이언트가 사용하는 파일 경로 (예: "/uploads/uuid_파일명.jpg")
     * @return 삭제 성공 여부
     */
    @Override
    public boolean deleteFile(String filePath) {
        try {
            // filePath에서 실제 파일명만 추출
            String filename = Paths.get(filePath).getFileName().toString();

            // 실제 저장된 파일 경로 조합
            File file = new File(uploadDir, filename);

            // 파일 존재 여부 확인 후 삭제 수행
            return file.exists() && file.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
