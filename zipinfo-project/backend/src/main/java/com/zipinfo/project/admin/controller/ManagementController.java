package com.zipinfo.project.admin.controller;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.admin.model.dto.BrokerApplicationDTO;
import com.zipinfo.project.admin.model.service.ManagementService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 관리자용 회원 관리 및 중개인 권한 신청 관련 API 컨트롤러
 * 
 * 기본 경로: /admin/management
 */
@RestController
@RequestMapping(
		"/admin/management")
public class ManagementController {

    private final ManagementService managementService;

    public ManagementController(ManagementService managementService) {
        this.managementService = managementService;
    }

    /**
     * 전체 회원 목록 조회 (삭제되지 않은 회원)
     * GET /admin/management/members
     */
    @GetMapping("/members")
    public ResponseEntity<List<Member>> getMembers() {
        List<Member> members = managementService.getAllMembers();
        return ResponseEntity.ok(members);
    }

    /**
     * 삭제된 회원 목록 조회
     * GET /admin/management/members/deleted
     */
    @GetMapping("/members/deleted")
    public ResponseEntity<List<Member>> getDeletedMembers() {
        List<Member> deletedMembers = managementService.getDeletedMembers();
        return ResponseEntity.ok(deletedMembers);
    }

    /**
     * 중개인 권한 신청 목록 조회
     * GET /admin/management/broker-applications
     */
    @GetMapping("/broker-applications")
    public ResponseEntity<List<BrokerApplicationDTO>> getBrokerApplications() {
        System.out.println("중개인 신청 목록 API 호출됨");
        List<BrokerApplicationDTO> list = managementService.getBrokerApplications();
        System.out.println("신청 개수: " + list.size());
        return ResponseEntity.ok(list);
    }

    /**
     * 중개인 신청 승인 처리
     * POST /admin/management/broker-applications/{memberNo}/approve
     * BODY: BrokerApplicationDTO (회사명, 사무소 위치 등 포함)
     */
    @PostMapping("/broker-applications/{memberNo}/approve")
    public ResponseEntity<String> approveBrokerApplication(
            @PathVariable Long memberNo,
            @RequestBody BrokerApplicationDTO dto) {

        dto.setMemberNumber(memberNo.intValue());

        boolean success = managementService.approveBroker(dto);
        if (success) {
            return ResponseEntity.ok("중개인 신청이 승인되었습니다.");
        }
        return ResponseEntity.badRequest().body("중개인 승인 처리 실패");
    }

    /**
     * 중개인 신청 거절 처리
     * PUT /admin/management/broker-applications/{memberNo}/reject
     */
    @PutMapping("/broker-applications/{memberNo}/reject")
    public ResponseEntity<String> rejectBrokerApplication(@PathVariable Long memberNo) {

        boolean success = managementService.rejectBroker(memberNo.intValue());
        if (success) {
            return ResponseEntity.ok("중개인 신청이 거절되었습니다.");
        }
        return ResponseEntity.badRequest().body("중개인 신청 거절 처리 실패");
    }


    /**
     * 회원 차단 및 차단 해제
     * PUT /admin/management/members/{memberNo}/block?block=true|false
     * @param memberNo 회원 번호
     * @param block true: 차단, false: 차단 해제
     */
    @PutMapping("/members/{memberNo}/block")
    public ResponseEntity<String> toggleBlockMember(
            @PathVariable Long memberNo,
            @RequestParam boolean block) {

        int result = managementService.toggleBlockMember(memberNo, block);
        if (result > 0) {
            return ResponseEntity.ok(block ? "회원이 차단되었습니다." : "회원 차단 해제되었습니다.");
        }
        return ResponseEntity.badRequest().body("회원 차단 변경 실패");
    }

    /**
     * 회원 삭제 (논리 삭제)
     * DELETE /admin/management/members/{memberNo}
     * @param memberNo 회원 번호
     */
    @DeleteMapping("/members/{memberNo}")
    public ResponseEntity<String> deleteMember(@PathVariable Long memberNo) {
        int result = managementService.deleteMember(memberNo);
        if (result > 0) {
            return ResponseEntity.ok("회원이 삭제되었습니다.");
        }
        return ResponseEntity.badRequest().body("회원 삭제 실패");
    }

    /**
     * 회원 복원
     * PUT /admin/management/members/{memberNo}/restore
     * @param memberNo 회원 번호
     */
    @PutMapping("/members/{memberNo}/restore")
    public ResponseEntity<String> restoreMember(@PathVariable Long memberNo) {
        int result = managementService.restoreMember(memberNo);
        if (result > 0) {
            return ResponseEntity.ok("회원이 복원되었습니다.");
        }
        return ResponseEntity.badRequest().body("회원 복원 실패");
    }
}
