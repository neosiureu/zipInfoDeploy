package com.zipinfo.project.member.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Member {
	protected int memberNo;
	protected String memberEmail;
	protected String memberPw;
	protected String memberImg;
	protected String enrollDate;
	protected String memberDelFl;
	protected String memberLogin;   
	protected  String memberNickname;
	protected String memberName;
	protected int memberAuth;
	protected String accessToken;
    

    
    private Member loginMember;

}
