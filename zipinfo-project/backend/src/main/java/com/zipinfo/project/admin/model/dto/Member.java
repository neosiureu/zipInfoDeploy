package com.zipinfo.project.admin.model.dto;

import lombok.Data;
import java.util.Date;

@Data
public class Member {
	private int memberNo;
	private String memberEmail;
	private String memberNickname;
	private String memberName;
	private int memberAuth;
	private String enrollDate;
	private String memberDelFl;
	private int postCount;  // 게시글 수
}

