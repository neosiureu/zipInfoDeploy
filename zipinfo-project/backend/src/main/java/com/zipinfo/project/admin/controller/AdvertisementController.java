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
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<Advertisement> registerAd(@RequestBody Advertisement ad) {
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
