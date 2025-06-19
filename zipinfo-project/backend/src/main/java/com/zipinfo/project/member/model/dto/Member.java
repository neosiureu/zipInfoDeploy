package com.zipinfo.project.member.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Member {
	private int memberNo;
	private String memberEmail;
	private String memberPw;
	private String memberImg;
	private String enrollDate;
	private String memberDelFl;
	private String memberLogin;   
	private  String memberNickname;
	private String memberName;
	private int memberAuth;
	private String accessToken;
	
    private String companyName; //중개사 명
    private String companyLocation; // 중개사 주소
    private  String brokerNo; // 중개등록번호
    private String presidentName; // 이름 (그냥 유저 이름이랑 똑같이 넣으면 됨)
    private String presidentPhone; // 대표번호
    

    
    private Member loginMember;

}
