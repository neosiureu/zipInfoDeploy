package com.zipinfo.project.member.model.service;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.zipinfo.project.member.model.dto.Member;

public interface MemberService {
    Member login(Member inputMember);

    int checkEmail(String memberEmail);

    int checkNickname(String memberNickname);

    /** 관리자 권한 부여
     * @param memberNo 회원 번호
     * @param newAuth 새 권한 코드 (예: 0=관리자)
     * @return 권한 변경 성공 여부
     */
    static boolean updateMemberAuth(Long memberNo, int newAuth) {
        // TODO Auto-generated method stub
        return false;
    }

    /** 
     * 이주원
     * 관리자 페이지 목록 조회 (권한별 회원 목록)
     * @param auth 권한 번호
     * @return 해당 권한을 가진 회원 리스트
     */
    static List<Member> getMembersByAuth(int auth) {
        // TODO Auto-generated method stub
        return null;
    }

    int checkBrokerNo(String brokerNo);

    /**
     * 이주원
     * 로그인 이메일로 회원 번호 조회
     * @param loginUserEmail 로그인한 회원 이메일 (또는 ID)
     * @return 회원 번호 (memberNo)
     */
    int findMemberNoByEmail(String loginUserEmail);  // static 빼고 인스턴스 메서드로 바꿈

    /**
     * 이주원
     * 회원 번호로 해당 회원이 관리자 권한인지 체크
     * @param memberNo 회원 번호
     * @return 관리자면 true, 아니면 false
     */
    boolean isAdmin(int memberNo);  // 추가 메서드 선언

	

	int signup(Member member);
    
    
}
