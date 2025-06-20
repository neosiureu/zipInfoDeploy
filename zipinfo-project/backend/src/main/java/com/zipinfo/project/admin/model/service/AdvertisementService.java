package com.zipinfo.project.admin.model.service;

import java.util.List;

import com.zipinfo.project.admin.model.dto.Advertisement;


public interface AdvertisementService {
    Advertisement registerAd(Advertisement ad);
    List<Advertisement> getAllAds();
    Advertisement updateAd(Advertisement ad);
    boolean deleteAd(int adId);
}
