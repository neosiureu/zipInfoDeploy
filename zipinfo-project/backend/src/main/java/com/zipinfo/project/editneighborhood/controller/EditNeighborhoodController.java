package com.zipinfo.project.editneighborhood.controller;

import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.FileOutputStream;

import org.springframework.beans.factory.annotation.Value;

import com.zipinfo.project.editneighborhood.model.service.EditneighborhoodService;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.neighborhood.model.dto.Neighborhood;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/editBoard")
@RequiredArgsConstructor
public class EditNeighborhoodController {

	@Value("${my.board.web-path}")
	private String boardWebPath;

	@Value("${my.board.folder-path}")
	private String boardFolderPath;
	private final EditneighborhoodService editneighborhoodService;
	
	 /** 게시글 작성 
		 * @param boardCode : 어떤 게시판에 작성할 글인지 구분 (1/2/3..)
		 * @param inputBoard : 입력된 값(제목, 내용) 세팅되어있음 (커맨드 객체)
		 * @param loginMember : 로그인한 회원 번호를 얻어오는 용도(세션에 등록되어있음)
		 * @throws Exception 
		 */
	@PostMapping("")
	public int boardInsert(
	    @RequestBody Neighborhood inputBoard,
	    @SessionAttribute("loginMember") Member loginMember
	) throws Exception {

	    int boardCode = 1;
	    inputBoard.setBoardCode(boardCode);
	    inputBoard.setMemberNo(loginMember.getMemberNo());

	    //  이미지 처리: Base64 => 파일 저장 => URL 변경
	    String processedContent = processImagesInContent(inputBoard.getBoardContent());
	    inputBoard.setBoardContent(processedContent);

	    System.out.println("서버에서 받은 우리동네게시판 보드는: " + inputBoard);

	    int boardNo = editneighborhoodService.boardInsert(inputBoard);
	    log.info("삽입할 보드 넘버" + boardNo);

	    return boardNo;
	}

	/**
	 * content 내의 Base64 이미지를 파일로 저장하고 URL로 변경
	 */
	private String processImagesInContent(String content) throws Exception {
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
	
	@PostMapping("/uploadImage")
	@ResponseBody
	public String uploadImage(@RequestParam("image") MultipartFile image) throws Exception {
	    
	    // 1. 폴더 확인
	    File dir = new File(boardFolderPath);
	    if (!dir.exists()) dir.mkdirs();
	    
	    // 2. 파일명 생성
	    String originalName = image.getOriginalFilename();
	    String ext = originalName.substring(originalName.lastIndexOf("."));
	    String fileName = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + ext;
	    
	    // 3. 파일 저장
	    File dest = new File(boardFolderPath, fileName);
	    image.transferTo(dest);
	    
	    // 4. 웹 경로 반환
	    return boardWebPath + fileName;  // /images/boardImg/abc123.jpg
	}

}
