package com.zipinfo.project.admin.controller;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.admin.model.dto.Advertisement;
import com.zipinfo.project.admin.model.service.AdvertisementService;

import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/advertisement")
public class AdvertisementController {

    private final AdvertisementService advertisementService;

    // 서버 메모리 내에서 광고를 관리하는 리스트
    private final List<Advertisement> adList = new CopyOnWriteArrayList<>();
    private final AtomicInteger adIdGenerator = new AtomicInteger(1); // 고유 ID 생성기

    public AdvertisementController(AdvertisementService advertisementService) {
        this.advertisementService = advertisementService;
    }

    /**
     * 파일 저장 + 메모리 리스트에 광고 추가 (DB 저장 없음)
     */
    @PostMapping("/register")
    public ResponseEntity<String> registerAd(@RequestParam("file") MultipartFile file) {
        try {
            String savedFilePath = advertisementService.saveFile(file);

            Advertisement newAd = new Advertisement();
            newAd.setId(adIdGenerator.getAndIncrement());
            newAd.setImageUrl(savedFilePath);

            adList.add(newAd);

            return ResponseEntity.status(HttpStatus.CREATED).body(savedFilePath);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("파일 저장 실패");
        }
    }




    /**
     * 등록된 광고 리스트 반환
     */
    @GetMapping("/list")
    public ResponseEntity<List<Advertisement>> getAdList() {
        return ResponseEntity.ok(adList);
    }

    /**
     * 광고 삭제 (메모리 리스트 제거 + 실제 이미지 파일 삭제)
     */
    @DeleteMapping("/delete/{adId}")
    public ResponseEntity<String> deleteAd(@PathVariable int adId) {
        Advertisement target = adList.stream()
                .filter(ad -> ad.getId() == adId)
                .findFirst()
                .orElse(null);

        if (target != null) {
            adList.remove(target);
            boolean deleted = advertisementService.deleteFile(target.getImageUrl());
            return ResponseEntity.ok(deleted ? "삭제 성공" : "파일 삭제 실패");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("광고를 찾을 수 없습니다.");
        }
    }
}
