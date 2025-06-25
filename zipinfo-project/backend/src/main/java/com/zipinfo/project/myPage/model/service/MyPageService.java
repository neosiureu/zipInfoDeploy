package com.zipinfo.project.myPage.model.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.stock.model.dto.Stock;

public interface MyPageService {

	Member getMemberInfo(Member loginMember);

	int updateInfo(Member loginMember, Member member);

	int checkPassword(Member loginMember, Member member);

	int updatePassword(Member loginMember, Member member);

	int checkNickname(Member loginMember, Member member);

	int withDraw(Member loginMember);

	int addStock(Stock stock);

	int addStockImg(List<MultipartFile> stockImg, int memberNo);

	int searchStockNo(int memberNo);

	int addCoord(Stock stock);

	List<Stock> getMyStock(int memberNo);

	int deleteStockInfo(int stockNo);


}
