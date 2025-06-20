package com.zipinfo.project.sale.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {
	private int saleStockNo;					// 매물번호
	private int memberNo;           			// 회원번호
	private int saleStockForm;      			// 매물형태
	private String saleStockName;      			// 매물명
	private int salePrice;          			// 분양가
	private String saleStatus;         			// 분양상태
	private String saleAddress;        			// 분양 매물주소
	private int scale;              			// 규모
	private String applicationStartDate;		// 청약 접수 시작일
	private String applicationEndDate; 			// 청약 접수 종료일
	private String announcementDate;   			// 당첨자 발표
	private String company;            			// 건설사
	private String contactInfo;        			// 분양문의
	private int acquisitionTax;     			// 취득세
	private int saleSupplyArea;     			// 공급 면적
	private int saleExclusiveArea;  			// 전용 면적
	private int saleRoomCount;      			// 방 개수
	private int saleBathroomCount;  			// 욕실 개수
	private int deposit;            			// 계약금
	private int middlePayment;      			// 중도금
	private int balancePayment;     			// 잔금
	private int regionNo;						// 법정동 코드
	
	// SALE_COORD에서 추가
	private double lat;
    private double lng;
}
