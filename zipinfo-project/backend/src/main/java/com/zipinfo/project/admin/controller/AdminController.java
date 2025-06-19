package com.zipinfo.project.admin.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.bind.annotation.SessionAttributes;

import com.zipinfo.project.admin.model.service.AdminService;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.service.MemberService;

import ch.qos.logback.core.model.Model;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("admin")
@Slf4j
@RequiredArgsConstructor
@SessionAttributes({ "loginMember" })
public class AdminController {
	private final AdminService service;
	@PostMapping("login")
	public Member login(@RequestBody Member inputMember, Model model) {
		Member loginMember = service.login(inputMember);
		if (loginMember == null) {
			return null;
		}
		
		return loginMember;
	}
	
	// 중개인 권한 부여
	@PutMapping("/admin/member/{memberNo}/authorize")
	public ResponseEntity<String> authorizeBroker(
	        @PathVariable Long memberNo,
	        @RequestParam("auth") int newAuth,
	        @SessionAttribute("loginMember") Member admin) {

	    // 관리자만 실행 가능
	    if (admin.getMemberAuth() != 0) {
	        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("접근 권한이 없습니다.");
	    }

	    try {
	        boolean updated = MemberService.updateMemberAuth(memberNo, newAuth);
	        return updated 
	            ? ResponseEntity.ok("회원 권한이 성공적으로 변경되었습니다.")
	            : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("회원 권한 변경 실패");
	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("권한 변경 중 오류: " + e.getMessage());
	    }
	}

	// 중개회원 권한 신청 목록 조회
	@GetMapping("/admin/broker-applications")
	public ResponseEntity<?> getBrokerApplicants(@SessionAttribute("loginMember") Member admin) {
	    if (admin.getMemberAuth() != 0) {
	        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("접근 권한이 없습니다.");
	    }

	    try {
	        List<Member> applicants = MemberService.getMembersByAuth(2); // 2: 중개 신청
	        return ResponseEntity.ok(applicants);
	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("중개회원 신청 목록 조회 중 오류: " + e.getMessage());
	    }
	}

	// 중개회원 신청 거절
	@PutMapping("/admin/member/{memberNo}/reject")
	public ResponseEntity<String> rejectBrokerApplication(
	        @PathVariable Long memberNo,
	        @SessionAttribute("loginMember") Member admin) {

	    if (admin.getMemberAuth() != 0) {
	        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("접근 권한이 없습니다.");
	    }

	    try {
	        // 권한을 일반회원(1)으로 되돌림
	        boolean result = MemberService.updateMemberAuth(memberNo, 1);
	        return result
	            ? ResponseEntity.ok("중개회원 신청이 거절되었습니다.")
	            : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("거절 처리 실패");
	    } catch (Exception e) {
	        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
	                .body("거절 처리 중 오류: " + e.getMessage());
	    }
	}

	
	/** 탈퇴 회원 리스트 조회 */
	@GetMapping("withdrawnMemberList")
	public ResponseEntity<Object> selectWithdrawnMemberList() {
		try {
			List<Member> withdrawnMemberList = service.selectWithdrawnMemberList();
			return ResponseEntity.status(HttpStatus.OK).body(withdrawnMemberList);
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("탈퇴한 회원 목록 조회 중 문제 발생 : " + e.getMessage());
		}
	}

	/** 탈퇴 회원 복구 */
	@PutMapping("withdrawnMember/restore/{memberNo}")
	public ResponseEntity<Object> restoreWithdrawnMember(
			@PathVariable("memberNo") Long memberNo,
			@SessionAttribute("loginMember") Member admin) {

		if (admin.getMemberAuth() != 0) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN).body("접근 권한이 없습니다.");
		}

		try {
			boolean result = service.restoreWithdrawnMember(memberNo);

			if (result) {
				return ResponseEntity.ok("탈퇴 회원 복구 완료");
			} else {
				return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("복구 실패: 존재하지 않거나 이미 활성화된 회원");
			}
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("복구 중 오류: " + e.getMessage());
		}
	}

}
	