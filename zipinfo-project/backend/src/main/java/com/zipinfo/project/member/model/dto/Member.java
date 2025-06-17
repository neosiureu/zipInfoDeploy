package com.zipinfo.project.member.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Member {
	int memberNo;
	String memberEmail;
	String memberPw;
	String memberImg;
	String enrollDate;
	String memberDelFl;
	String memberLogin;   
    String memberNickname;
    String memberName;
    int memberAuth;
    String accessToken;
    

}
