package com.zipinfo.project.admin.controller;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.admin.model.dto.BrokerApplicationDTO;
import com.zipinfo.project.admin.model.service.ManagementService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/management")
public class ManagementController {

    private final ManagementService managementService;

    public ManagementController(ManagementService managementService) {
        this.managementService = managementService;
    }

    @GetMapping("/members")
    public ResponseEntity<List<Member>> getMembers() {
        List<Member> members = managementService.getAllMembers();
        return ResponseEntity.ok(members);
    }

    @GetMapping("/members/deleted")
    public ResponseEntity<List<Member>> getDeletedMembers() {
        List<Member> deletedMembers = managementService.getDeletedMembers();
        return ResponseEntity.ok(deletedMembers);
    }

    @GetMapping("/broker-applications")
    public ResponseEntity<List<BrokerApplicationDTO>> getBrokerApplications() {
        List<BrokerApplicationDTO> apps = managementService.getBrokerApplications();
        return ResponseEntity.ok(apps);
    }

    @PutMapping("/broker-applications/{memberNo}/status")
    public ResponseEntity<String> updateApplicationStatus(
            @PathVariable Long memberNo,
            @RequestParam String status) {

        int result = managementService.updateBrokerApplicationStatus(memberNo, status);
        if (result > 0) {
            return ResponseEntity.ok("신청 상태가 변경되었습니다.");
        }
        return ResponseEntity.badRequest().body("신청 상태 변경 실패");
    }

    @PutMapping("/members/{memberNo}/role")
    public ResponseEntity<String> updateMemberRole(
            @PathVariable Long memberNo,
            @RequestParam int authId) {

        int result = managementService.updateMemberAuth(memberNo, authId);
        if (result > 0) {
            return ResponseEntity.ok("회원 권한이 변경되었습니다.");
        }
        return ResponseEntity.badRequest().body("회원 권한 변경 실패");
    }

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

    @DeleteMapping("/members/{memberNo}")
    public ResponseEntity<String> deleteMember(@PathVariable Long memberNo) {
        int result = managementService.deleteMember(memberNo);
        if (result > 0) {
            return ResponseEntity.ok("회원이 삭제되었습니다.");
        }
        return ResponseEntity.badRequest().body("회원 삭제 실패");
    }

    @PutMapping("/members/{memberNo}/restore")
    public ResponseEntity<String> restoreMember(@PathVariable Long memberNo) {
        int result = managementService.restoreMember(memberNo);
        if (result > 0) {
            return ResponseEntity.ok("회원이 복원되었습니다.");
        }
        return ResponseEntity.badRequest().body("회원 복원 실패");
    }
}
