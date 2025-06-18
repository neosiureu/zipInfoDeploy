package com.zipinfo.project.member.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@AllArgsConstructor
@NoArgsConstructor
@Data
public class BrokerInfo extends Member {
    private String companyName; //중개사 명
    private String companyLocation; // 중개사 주소
    private  String brokerNo; // 중개등록번호
    private String presidentName; // 이름 (그냥 유저 이름이랑 똑같이 넣으면 됨)
    private String presidentPhone; // 대표번호
}
