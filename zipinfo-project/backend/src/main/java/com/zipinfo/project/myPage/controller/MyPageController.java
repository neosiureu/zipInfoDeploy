package com.zipinfo.project.myPage.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.myPage.model.service.MyPageService;
import com.zipinfo.project.stock.model.dto.Stock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequestMapping("myPage")
@Slf4j
@SessionAttributes({"loginMember"})
@RestController
@RequiredArgsConstructor
public class MyPageController {

	private final MyPageService service;
	
	@GetMapping("memberInfo")
	public ResponseEntity<Object> memberInfo(HttpSession session){
		
		try {
			
			Member loginMember = (Member)session.getAttribute("loginMember");
			
			Member member = service.getMemberInfo(loginMember);
			
			System.out.println(member);
			
			
			if (member.getCompanyLocation() != null) {
				String[] arr = member.getCompanyLocation().split("\\^\\^\\^");

				// 초기화
				String postcode = null;
				String address = null;
				String detailAddress = null;

				if (arr.length > 0)
					postcode = arr[0];
				if (arr.length > 1)
					address = arr[1];
				if (arr.length > 2)
					detailAddress = arr[2];
				
				String finalAddress = address +" " + detailAddress;

				member.setAddress(address);
				member.setDetailAddress(detailAddress);
				member.setPostcode(postcode);
				member.setCompanyLocation(finalAddress);

			}
			
			System.out.println(member);
			
			return ResponseEntity.status(HttpStatus.OK) // 200
				   .body(member); 
			
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				   .body("불러오는 중 예외 발생 : " + e.getMessage());
			
		}
	}
	
	@PostMapping("updateInfo")
	public ResponseEntity<Object> updateInfo(HttpSession session, @RequestBody Member member){
		
		try {
			
			Member loginMember = (Member)session.getAttribute("loginMember");

			int result = service.updateInfo(loginMember,member);
			
			return ResponseEntity.status(HttpStatus.OK) // 200
					.body(result); 
			
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("불러오는 중 예외 발생 : " + e.getMessage());
			
		}
	}
	
	@PostMapping("checkPassword")
	public ResponseEntity<Object> checkPassword(HttpSession session, @RequestBody Member member){
		
		try {
			
			Member loginMember = (Member)session.getAttribute("loginMember");
			
			int result = service.checkPassword(loginMember,member);
			
			return ResponseEntity.status(HttpStatus.OK) // 200
					.body(result); 
			
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("불러오는 중 예외 발생 : " + e.getMessage());
			
		}
	}
	
	
	@PostMapping("updatePassword")
	public ResponseEntity<Object> updatePassword(HttpSession session, @RequestBody Member member){
		
		try {
			
			Member loginMember = (Member)session.getAttribute("loginMember");
			
			int result = service.updatePassword(loginMember,member);
			
			return ResponseEntity.status(HttpStatus.OK) // 200
					.body(result); 
			
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("불러오는 중 예외 발생 : " + e.getMessage());
			
		}
	}
	
	@PostMapping("checkNickname")
	public ResponseEntity<Object> checkNickname(HttpSession session, @RequestBody Member member){
		
		try {
			
			Member loginMember = (Member)session.getAttribute("loginMember");
			
			int result = service.checkNickname(loginMember, member);
		
			return ResponseEntity.status(HttpStatus.OK) // 200
					.body(result); 
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("불러오는 중 예외 발생 : " + e.getMessage());
		}
	}
	
	@GetMapping("withDraw")
	public ResponseEntity<Object> withDraw(HttpSession session){
		
		try {
			
			Member loginMember = (Member)session.getAttribute("loginMember");
			
			int result = service.withDraw(loginMember);
		
			return ResponseEntity.status(HttpStatus.OK) // 200
					.body(result); 
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("불러오는 중 예외 발생 : " + e.getMessage());
		}
		
	}
	
	@PostMapping("addStock")
	public ResponseEntity<Object> addStock(HttpSession session,  @RequestBody Stock stock){
		
		try {
			
			Member loginMember = (Member)session.getAttribute("loginMember");
			
	        if (loginMember == null) {
	            System.out.println("세션에 로그인 정보 없음");
	            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
	                    .body("로그인 정보 없음 (세션 만료 혹은 미로그인)");
	        }
			
			stock.setMemberNo(loginMember.getMemberNo());
			
			int result = service.addStock(stock);
			
			
		
			return ResponseEntity.status(HttpStatus.OK) // 200
					.body(result); 
		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("불러오는 중 예외 발생 : " + e.getMessage());
		}
		
	}
	
	@PostMapping(value = "addStockImg", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Object> addStockImg(HttpSession session, @RequestPart("stockImg") List<MultipartFile> stockImg){
		
		System.out.println(stockImg);
		try {
			
			Member loginMember = (Member)session.getAttribute("loginMember");
			
			int memberNo = loginMember.getMemberNo();
			
			int imgResult = service.addStockImg(stockImg, memberNo);
		
			return ResponseEntity.status(HttpStatus.OK) // 200
					.body(imgResult); 
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body("불러오는 중 예외 발생 : " + e.getMessage());
		}
		
	}
}
