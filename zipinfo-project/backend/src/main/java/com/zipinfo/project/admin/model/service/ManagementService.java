package com.zipinfo.project.admin.model.service;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.admin.model.dto.BrokerApplicationDTO;

import java.util.List;

/**
 * 관리자 관련 서비스 인터페이스
 * 회원 조회, 삭제, 복원, 권한 변경 및 중개인 신청 처리 기능 제공
 */
public interface ManagementService {

    /**
     * 삭제되지 않은 전체 회원 목록 조회
     * @return 회원 리스트
     */
    List<Member> getAllMembers();

    /**
     * 삭제된 회원 목록 조회 (논리삭제된 회원)
     * @return 삭제 회원 리스트
     */
    List<Member> getDeletedMembers();

    /**
     * 중개인 권한 신청 목록 조회
     * @return 중개인 신청 정보 리스트
     */
    List<BrokerApplicationDTO> getBrokerApplications();

    /**
     * 중개인 신청 상태 업데이트
     * (기존: 상태 문자열 기반 - 사용하지 않는다면 삭제 가능)
     * @param memberNo 회원 번호
     * @param status 신청 상태 (예: "승인", "거절" 등)
     * @return 업데이트 결과 (1 이상 성공)
     */
    int updateBrokerApplicationStatus(Long memberNo, String status);

    /**
     * 중개인 신청 승인 처리
     * - MEMBER_AUTH = 3 으로 변경
     * - BROKER_INFO 테이블에 중개사 정보 등록
     * @param dto 중개인 신청 정보 (회사명, 주소 등 포함)
     * @return 성공 여부
     */
    boolean approveBroker(BrokerApplicationDTO dto);

    /**
     * 중개인 신청 거절 처리
     * - MEMBER_AUTH = 1 (일반회원)로 변경
     * @param memberNo 회원 번호
     * @return 성공 여부
     */
    boolean rejectBroker(int memberNo);

    /**
     * 회원 권한 변경
     * @param memberNo 회원 번호
     * @param authId 권한 ID (0: 관리자, 1: 일반회원, 2: 중개인 신청, 3: 중개인)
     * @return 업데이트 결과 (1 이상 성공)
     */
    int updateMemberAuth(Long memberNo, int authId);

    /**
     * 회원 차단 또는 차단 해제 처리
     * @param memberNo 회원 번호
     * @param block true: 차단, false: 차단 해제
     * @return 업데이트 결과 (1 이상 성공)
     */
    int toggleBlockMember(Long memberNo, boolean block);

    /**
     * 회원 삭제 처리 (논리 삭제)
     * @param memberNo 회원 번호
     * @return 삭제 결과 (1 이상 성공)
     */
    int deleteMember(Long memberNo);

    /**
     * 논리 삭제된 회원 복원 처리
     * @param memberNo 회원 번호
     * @return 복원 결과 (1 이상 성공)
     */
    int restoreMember(Long memberNo);
}
