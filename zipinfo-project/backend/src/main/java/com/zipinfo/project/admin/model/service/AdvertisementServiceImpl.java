package com.zipinfo.project.admin.model.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.admin.model.dto.Advertisement;

@Service
public class AdvertisementServiceImpl implements AdvertisementService {

    // 임시 저장소 (DB 대신)
    private final List<Advertisement> adList = new ArrayList<>();
    private int idSequence = 1;

    // 실제 파일 저장 폴더 경로 (환경에 맞게 수정)
    private final String uploadDir = "C:/upload/advertisements/"; 

    @Override
    public Advertisement registerAd(Advertisement ad) {
        ad.setId(idSequence++);
        adList.add(ad);
        return ad;
    }

    @Override
    public List<Advertisement> getAllAds() {
        return adList;
    }

    @Override
    public Advertisement updateAd(Advertisement ad) {
        Optional<Advertisement> existingAdOpt = adList.stream()
                .filter(a -> a.getId() == ad.getId())
                .findFirst();

        if(existingAdOpt.isPresent()) {
            Advertisement existingAd = existingAdOpt.get();
            existingAd.setTitle(ad.getTitle());
            existingAd.setContent(ad.getContent());
            existingAd.setStartDate(ad.getStartDate());
            existingAd.setEndDate(ad.getEndDate());
            existingAd.setImageUrl(ad.getImageUrl());
            existingAd.setAuthor(ad.getAuthor());
            existingAd.setMain(ad.isMain());
            return existingAd;
        }
        return null;
    }

    @Override
    public boolean deleteAd(int adId) {
        return adList.removeIf(ad -> ad.getId() == adId);
    }

    @Override
    public String saveFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("업로드할 파일이 없습니다.");
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String ext = "";

            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            // UUID로 고유한 파일명 생성
            String savedFilename = UUID.randomUUID().toString() + ext;

            // 저장 폴더가 없으면 생성
            File dir = new File(uploadDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            // 파일 저장
            Files.copy(file.getInputStream(), Paths.get(uploadDir + savedFilename));

            // 파일 접근용 URL 반환 (환경에 맞게 수정)
            return "/uploads/advertisements/" + savedFilename;

        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }
    }
}
