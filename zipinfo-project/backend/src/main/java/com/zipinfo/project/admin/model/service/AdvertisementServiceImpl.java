package com.zipinfo.project.admin.model.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.zipinfo.project.admin.model.dto.Advertisement;

@Service
public class AdvertisementServiceImpl implements AdvertisementService {

    // 임시 저장소 (DB대신)
    private final List<Advertisement> adList = new ArrayList<>();
    private int idSequence = 1;

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
            return existingAd;
        }
        return null;
    }

    @Override
    public boolean deleteAd(int adId) {
        return adList.removeIf(ad -> ad.getId() == adId);
    }
}
