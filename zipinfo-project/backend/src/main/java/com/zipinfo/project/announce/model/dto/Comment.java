package com.zipinfo.project.announce.model.dto;

import org.springframework.web.multipart.MultipartFile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Comment {
	
	private int commentNo;
	private String commentContent;
	private String commentDate;
	private String commentDelFl;
	private int boardNo;
	private int memberNo;
	private int commentParentNo;
	
	// 댓글 조회 시 회원 프로필, 닉네임
	private String memberNickname;
	private String profileImg;
	
	
}