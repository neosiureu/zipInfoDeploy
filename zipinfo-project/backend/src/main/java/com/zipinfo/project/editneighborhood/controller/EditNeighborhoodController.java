package com.zipinfo.project.editneighborhood.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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

	/** 이주원
	 * 게시글 작성
	 * @param boardCode   : 어떤 게시판에 작성할 글인지 구분 (1/2/3..)
	 * @param inputBoard  : 입력된 값(제목, 내용) 세팅되어있음 (커맨드 객체)
	 * @param loginMember : 로그인한 회원 번호를 얻어오는 용도(세션에 등록되어있음)
	 * @throws Exception
	 */
	@PostMapping("")
	public int boardInsert(@RequestBody Neighborhood inputBoard, @AuthenticationPrincipal Member loginMember)
			throws Exception {

		int boardCode = 1;
		inputBoard.setBoardCode(boardCode);
		inputBoard.setMemberNo(loginMember.getMemberNo());

		// 이미지 처리: Base64 => 파일 저장 => URL 변경
		String processedContent = editneighborhoodService.processImagesInContent(inputBoard.getBoardContent());
		inputBoard.setBoardContent(processedContent);

		System.out.println("서버에서 받은 우리동네게시판 보드는: " + inputBoard);

		int boardNo = editneighborhoodService.boardInsert(inputBoard);
		log.info("삽입할 보드 넘버" + boardNo);

		return boardNo;
	}

	/** 이주원
	 * 게시글 삭제
	 * @param inputBoard 
	 * @throws Exception
	 */
	@DeleteMapping("{boardNo}")
	public int boardDelete(@PathVariable("boardNo") int boardNo, @AuthenticationPrincipal Member loginMember)
			throws Exception {

		
		Neighborhood inputBoard = new Neighborhood();
		inputBoard.setBoardNo(boardNo);
		inputBoard.setMemberNo(loginMember.getMemberNo());
		
		
		log.info("내 닉네임은"+loginMember.getMemberNickname()+"입니다");

		
//		System.out.println("서버에서 받은 삭제해야 할 우리동네게시판 보드는: " + boardNo);

		int deleted = editneighborhoodService.boardDelete(inputBoard);
//		log.info("삭제된 열의 개수" + deleted);


		return deleted;
	}
	
	
	/** 이주원
	 * 게시글 수정
	 * @param inputBoard  : 입력된 값(제목, 내용) 세팅되어있음 (커맨드 객체)
	 * @param loginMember : 로그인한 회원 번호를 얻어오는 용도(세션에 등록되어있음)
	 * @throws Exception
	 */
	@PutMapping("")
	public int boardUpdate(@RequestBody Neighborhood inputBoard, @AuthenticationPrincipal Member loginMember)
			throws Exception {

		int boardCode = 1;
		inputBoard.setBoardCode(boardCode);
		inputBoard.setMemberNo(loginMember.getMemberNo());

		// 이미지 처리: Base64 => 파일 저장 => URL 변경
		String processedContent = editneighborhoodService.processImagesInContent(inputBoard.getBoardContent());
		inputBoard.setBoardContent(processedContent);

//		System.out.println("서버에서 받은 수정해야 할 우리동네게시판 보드는: " + inputBoard);

		int boardNo = editneighborhoodService.boardUpdate(inputBoard);
//		log.info("수정의 결과" + boardNo);

		return boardNo;
	}
	

	@PostMapping("/uploadImage")
	@ResponseBody
	public String uploadImage(@RequestParam("image") MultipartFile image) throws Exception {

		// 1. 폴더 확인
		File dir = new File(boardFolderPath);
		if (!dir.exists())
			dir.mkdirs();

		// 2. 파일명 생성
		String originalName = image.getOriginalFilename();
		String ext = originalName.substring(originalName.lastIndexOf("."));
		String fileName = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + ext;

		// 3. 파일 저장
		File dest = new File(boardFolderPath, fileName);
		image.transferTo(dest);

		// 4. 웹 경로 반환
		return boardWebPath + fileName; // /images/boardImg/img.jpg
	}

}
