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
	private String memberLogin;   // E이면 그냥 이메일 가입, K이면 카카오 가입, N이면 네이버 가입
	private  String memberNickname;
	private String memberName;
	private int memberAuth;
	private String accessToken;
	private String memberLocation;  // 관심 주소 => DB저장용


	
    private String companyName; //중개사 명
    private String companyLocation; // 중개사 주소 =>  DB저장용
    private  String brokerNo; // 중개등록번호
    private String presidentName; // 이름 (그냥 유저 이름이랑 똑같이 넣으면 됨)
    private String presidentPhone; // 대표번호
    
    private String postcode;
    private String address;
    private String detailAddress;
    
    
    private String companyPostcode;
    private String companyAddress;
    private String companyDetailAddress;
    
    private Member loginMember;
    

}