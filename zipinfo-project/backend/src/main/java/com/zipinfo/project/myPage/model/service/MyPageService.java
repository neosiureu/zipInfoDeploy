package com.zipinfo.project.myPage.model.service;

import java.util.List;
import java.util.Map;

import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.admin.model.dto.HelpMessage;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.neighborhood.model.dto.Neighborhood;
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

	int updateStock(Stock stock);

	int updateCoord(Stock stock);

	int updateTumbImg(MultipartFile stockImg, int stockNo);

	int updateBalanceImg(MultipartFile stockImg, int stockNo);

	int updateStockImg(List<MultipartFile> stockImg, int stockNo);

	List<Stock> getSawStock(int memberNo);

	List<Stock> getLikeStock(int memberNo);

	List<Neighborhood> getMyPost(int memberNo);

	int unlikeStock(Stock stock);

	int likeStock(Stock stock);

	int updateSellYn(Stock stock);

	int sendMessage(MultipartFile messageFile, HelpMessage message);

	List<HelpMessage> getMyMessage(int memberNo);

	HelpMessage getMessageContent(int messageNo);

	HelpMessage getInquiredMessage(int messageNo);

	HelpMessage getMessageFile(int messageNo);

	int addSawStock(Stock stock);

	Map<String, Object> searchResult(String value);


}
