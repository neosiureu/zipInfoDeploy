package com.zipinfo.project.editneighborhood.model.service;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.editneighborhood.model.mapper.EditNeighborhoodMapper;
import com.zipinfo.project.neighborhood.model.dto.Neighborhood;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional (rollbackFor = Exception.class)
@Slf4j
public class EditneighborhoodServiceImpl implements EditneighborhoodService {
	@Value("${my.board.web-path}")
	private String boardWebPath;

	@Value("${my.board.folder-path}")
	private String boardFolderPath;
	private final EditNeighborhoodMapper editNeighborhoodMapper;

	@Override
	public int boardInsert(Neighborhood inputBoard) {

	int result= editNeighborhoodMapper.boardInsert(inputBoard);
	return result;
	}

	
	/**
	 * content 내의 Base64 이미지를 파일로 저장하고 URL로 변경
	 */
	public String processImagesInContent(String content) throws Exception {
	    if (content == null || !content.contains("data:image")) {
	        return content;
	    }

	    // Base64 이미지 패턴 찾기
	    Pattern pattern = Pattern.compile("data:image/([^;]+);base64,([^\"]+)");
	    Matcher matcher = pattern.matcher(content);
	    
	    StringBuffer processedContent = new StringBuffer();
	    
	    while (matcher.find()) {
	        String imageFormat = matcher.group(1); // jpeg, png 등
	        String base64Data = matcher.group(2);
	        
	        // Base64 => 파일 저장
	        String imageUrl = saveBase64Image(base64Data, imageFormat);
	        
	        // content에서 Base64를 파일 URL로 교체
	        matcher.appendReplacement(processedContent, imageUrl);
	    }
	    
	    matcher.appendTail(processedContent);
	    return processedContent.toString();
	}

	/**
	 * Base64 이미지를 파일로 저장하고 웹 URL 반환
	 */
	private String saveBase64Image(String base64Data, String format) throws Exception {
	    // 1. 폴더 확인
	    File dir = new File(boardFolderPath);
	    if (!dir.exists()) dir.mkdirs();
	    
	    // 2. 파일명 생성
	    String extension = format.equals("jpeg") ? ".jpg" : "." + format;
	    String fileName = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + extension;
	    
	    // 3. Base64 디코딩 후 파일 저장
	    byte[] imageBytes = Base64.getDecoder().decode(base64Data);
	    File dest = new File(boardFolderPath, fileName);
	    
	    try (FileOutputStream fos = new FileOutputStream(dest)) {
	        fos.write(imageBytes);
	    }
	    
	    // 4. 웹 URL 반환
	    return "http://localhost:8080" + boardWebPath + fileName;
	}


	@Override
	public int boardUpdate(Neighborhood inputBoard) {
		int result= editNeighborhoodMapper.boardUpdate(inputBoard);
		return result;
	}


	@Override
	public int boardDelete(Neighborhood inputBoard) {
		
		return editNeighborhoodMapper.boardDelete(inputBoard);
	}


	
	
}
