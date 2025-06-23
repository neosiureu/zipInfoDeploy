package com.zipinfo.project.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.admin.model.dto.Advertisement;
import com.zipinfo.project.admin.model.service.AdvertisementService;

@RestController
@RequestMapping("/advertisement")
public class AdvertisementController {

    private final AdvertisementService advertisementService;

    public AdvertisementController(AdvertisementService advertisementService) {
        this.advertisementService = advertisementService;
    }

    @PostMapping("/register")
    public ResponseEntity<Advertisement> registerAd(
            @RequestParam("title") String title,
            @RequestParam("content") String content,
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("author") String author,
            @RequestParam(value = "isMain", required = false, defaultValue = "false") boolean isMain,
            @RequestPart("file") MultipartFile file) {

        // 1. 파일 저장 (서비스에 위임)
        String savedFilePath = advertisementService.saveFile(file);

        // 2. Advertisement 객체 생성
        Advertisement ad = new Advertisement();
        ad.setTitle(title);
        ad.setContent(content);
        ad.setStartDate(startDate);
        ad.setEndDate(endDate);
        ad.setAuthor(author);
        ad.setMain(isMain);
        ad.setImageUrl(savedFilePath);

        // 3. DB 저장
        Advertisement savedAd = advertisementService.registerAd(ad);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedAd);
    }
    
    @GetMapping("/list")
    public ResponseEntity<List<Advertisement>> getAdList() {
        List<Advertisement> ads = advertisementService.getAllAds();
        return ResponseEntity.ok(ads);
    }

    @PutMapping("/update/{adId}")
    public ResponseEntity<Advertisement> updateAd(@PathVariable int adId, @RequestBody Advertisement ad) {
        ad.setId(adId);
        Advertisement updatedAd = advertisementService.updateAd(ad);
        return ResponseEntity.ok(updatedAd);
    }

    @DeleteMapping("/delete/{adId}")
    public ResponseEntity<String> deleteAd(@PathVariable int adId) {
        boolean deleted = advertisementService.deleteAd(adId);
        if(deleted) {
            return ResponseEntity.ok("삭제 성공");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("삭제 실패");
        }
    }
}
