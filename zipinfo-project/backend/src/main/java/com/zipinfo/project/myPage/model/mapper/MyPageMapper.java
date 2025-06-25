package com.zipinfo.project.myPage.model.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.stock.model.dto.Stock;

@Mapper
public interface MyPageMapper {

	Member getMemberInfo(Member loginMember);

	int updateNormalInfo(Member member);

	int updateBrokerInfo(Member member);

	String getMemberPassword(Member loginMember);

	int updatePassword(Member member);

	Integer checkNickname(Member member);

	Member compareInfo(Member member);

	int changeAuth(Member member);

	int withDraw(Member loginMember);

	int addStock(Stock stock);

	int addStockImg(@Param("originalName") String originalName,
            @Param("rename") String rename,
            @Param("i") int i,
            @Param("finalPath") String finalPath,
            @Param("stockNo") int stockNo);
	
	int searchStockNo(int memberNo);

	int addCoord(Stock stock);

	List<Stock> getMyStock(int memberNo);

	List<Stock> selectImgUrl(int stockNo);

	int deleteStockInfo(int stockNo);

	Stock selectStockCoord(int stockNo);

}
