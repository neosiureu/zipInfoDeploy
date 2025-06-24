package com.zipinfo.project.myPage.model.service;


import java.io.File;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.zipinfo.project.common.utility.Utility;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.myPage.model.mapper.MyPageMapper;
import com.zipinfo.project.stock.model.dto.Stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Transactional(rollbackFor = Exception.class)
@Service
@RequiredArgsConstructor
@Slf4j
public class MyPageServiceImpl implements MyPageService{

	private final MyPageMapper mapper;
	
	@Autowired
	private BCryptPasswordEncoder bcrypt;
	
	@Value("${my.stock.web-path}")
	private String stockWebPath;
	
	@Value("${my.stock.folder-path}")
	private String stockFolderPath;
	
	@Override
	public Member getMemberInfo(Member loginMember) {
		
			return mapper.getMemberInfo(loginMember);
		}
	
	@Override
	public int updateInfo(Member loginMember, Member member) {
		
		int result;
		
		member.setMemberNo(loginMember.getMemberNo());
		
		if(loginMember.getMemberAuth() == 3) {
			
			Member compareInfo = mapper.compareInfo(member);
			
			if(!compareInfo.getCompanyName().equals(member.getCompanyName()) || 
				!compareInfo.getCompanyName().equals(member.getCompanyLocation()) ||
				!compareInfo.getPresidentName().equals(member.getPresidentName()) ||
				!compareInfo.getPresidentPhone().equals(member.getPresidentPhone()) ||
				!compareInfo.getBrokerNo().equals(member.getBrokerNo())) {
				int changeAuth = mapper.changeAuth(member);
			}
			
			int firstResult = mapper.updateNormalInfo(member);
			
			int secondResult = mapper.updateBrokerInfo(member);
			
			result = firstResult + secondResult;
			
		}else {
			result =  mapper.updateNormalInfo(member);
		}
		
		return result;
	}
	
	@Override
	public int checkPassword(Member loginMember, Member member) {
		
		String memberPassword = mapper.getMemberPassword(loginMember);
		
		if(!bcrypt.matches(member.getMemberPw(), memberPassword)) {
			return 0;
		}
		
		return 1;
	}
	
	@Override
	public int updatePassword(Member loginMember, Member member) {
		
		String encPw = bcrypt.encode(member.getMemberPw());
		member.setMemberPw(encPw);
		member.setMemberNo(loginMember.getMemberNo());
		
		return mapper.updatePassword(member);
	}
	
	@Override
	public int checkNickname(Member loginMember, Member member) {
		
		member.setMemberNo(loginMember.getMemberNo());
		
		Integer result = mapper.checkNickname(member);
		
		return result != null ? 1: 0;
	}
	
	@Override
	public int withDraw(Member loginMember) {
		return mapper.withDraw(loginMember);
	}
	
	@Override
	public int addStock(Stock stock) {
		int result = mapper.addStock(stock);
	    System.out.println("insert result = " + result);
	    return result;
	}
	
	@Override
	public int addStockImg(List<MultipartFile> stockImg, int memberNo) {

		try {
			
			String finalPath = null;
			
			String originalName = null;
			String rename = null;
			
			// 성공했는지 확인용 변수
			int result = 0;
			int totalResult;
			
			int stockNo = mapper.searchStockNo(memberNo);
			
			for(int i = 0; i<stockImg.size(); i++) {
				
				MultipartFile file = stockImg.get(i);
				
				originalName = file.getOriginalFilename();
				rename = Utility.fileRename(originalName);
				
				File saveFile = new File(stockFolderPath, rename);
				
				// 디렉토리가 없으면 생성
				saveFile.getParentFile().mkdirs();
				
				// /myPage/stock/변경된 파일명
				finalPath = stockWebPath + rename;
				file.transferTo(saveFile);
				
				totalResult = mapper.addStockImg(originalName, rename, i, finalPath, stockNo);
				
				if(totalResult == 1) result++;
			}
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}
}
