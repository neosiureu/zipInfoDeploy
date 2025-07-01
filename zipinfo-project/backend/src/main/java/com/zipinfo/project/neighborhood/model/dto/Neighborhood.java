package com.zipinfo.project.neighborhood.model.dto;

import java.util.List;

import com.zipinfo.project.announce.model.dto.Comment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Neighborhood {
	private int boardNo;
	private String boardTitle;
	private String boardContent;
	private String boardWriteDate;
	private int readCount;
	private String boardDelFl;
	private int memberNo;
	private String memberNickName;
	private String boardSubject;
	private String townName;
	private String cityName;
	private int townNo;
	private int cityNo;
	private int boardCode;
	private int likeCount;

    private List<Comment> commentList; // 임시 주석
}
