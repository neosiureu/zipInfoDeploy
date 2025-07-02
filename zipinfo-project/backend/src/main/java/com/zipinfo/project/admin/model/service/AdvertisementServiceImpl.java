package com.zipinfo.project.admin.model.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.admin.model.dto.Advertisement;
import com.zipinfo.project.admin.model.mapper.AdvertisementMapper;
import com.zipinfo.project.common.utility.Utility;
import com.zipinfo.project.myPage.model.mapper.MyPageMapper;
import com.zipinfo.project.myPage.model.service.MyPageServiceImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Transactional(rollbackFor = Exception.class)
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvertisementServiceImpl implements AdvertisementService {

	@Value("${my.advertise.web-path}")
	private String adWebPath;
	
	@Value("${my.advertise.folder-path}")
	private String adFolderPath;
	
	private final AdvertisementMapper mapper;

    /**
     * 서버에 파일 저장 후 클라이언트에서 접근 가능한 경로 문자열 반환
     * @param file - 업로드된 MultipartFile
     * @return 저장된 파일의 웹 접근 경로 (예: "/uploads/uuid_파일명.jpg")
     */
    @Override
    public int saveFile(MultipartFile file, int memberNo) {
        try {
        	
			String finalPath = null;
			
			String originalName = null;
			String rename = null;
			
			// 성공했는지 확인용 변수
			int result = 0;
			int totalResult;
			
			originalName = file.getOriginalFilename();
			rename = Utility.fileRename(originalName);
			
			File saveFile = new File(adFolderPath, rename);
			
			// 디렉토리가 없으면 생성
			saveFile.getParentFile().mkdirs();
			
			// /myPage/stock/변경된 파일명
			finalPath = adWebPath + rename;
			file.transferTo(saveFile);
			
			result = mapper.saveFile(originalName, rename, finalPath, memberNo);

            return result;


        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("파일 저장 실패", e);
        }
    }
    
    @Override
    public List<Advertisement> getAdList() {
    	return mapper.getAdList();
    }
    
    @Override
    public int updateMain(int adNo) {
    	int updateN = mapper.updateMainN();
    	
    	int updateY = mapper.updateMainY(adNo);
    	
    	if(updateN + updateY == 2) {
    		return 2;
    	}else {
    		return 0;
    	}
    }
    
    @Override
    public int deleteAd(int adNo) {
    	return mapper.deleteAd(adNo);
    }
}
