package com.zipinfo.project.myPage.model.mapper;

import org.apache.ibatis.annotations.Mapper;

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

}
