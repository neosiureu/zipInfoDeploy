package com.zipinfo.project.admin.model.service;

import java.util.List;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.admin.model.mapper.AdminMapper;
import com.zipinfo.project.common.utility.Utility;
import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.mapper.MemberMapper;

import lombok.RequiredArgsConstructor;


@Transactional(rollbackFor = Exception.class)
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper mapper;
    private final MemberMapper mMapper;
    private final BCryptPasswordEncoder bcrypt; // ✅ 여기에 추가


    /**
     * 관리자 로그인 처리
     * 
     * @param inputMember 입력받은 회원 정보 (이메일, 비밀번호)
     * @return 로그인 성공 시 회원 정보 반환, 실패 시 null 반환
     */
    @Override
    public Member login(Member inputMember) {
        System.out.println("=== AdminServiceImpl.login 호출됨 ===");
        System.out.println("입력된 이메일: " + inputMember.getMemberEmail());
        System.out.println("입력된 비밀번호: " + inputMember.getMemberPw());
        
        // 이메일로 회원 조회
        Member loginMember = mapper.login(inputMember.getMemberEmail());
        System.out.println("DB에서 조회된 회원: " + loginMember);

        // 회원이 없으면 null 반환
        if (loginMember == null) {
            System.out.println("회원이 없음");
            return null;
        }

        // 비밀번호 일치 여부 확인 (단순 문자열 비교 - 실제 운영 시 암호화 필요)
        System.out.println("DB 비밀번호: " + loginMember.getMemberPw());
        System.out.println("입력 비밀번호: " + inputMember.getMemberPw());
        System.out.println("비밀번호 일치: " + loginMember.getMemberPw().equals(inputMember.getMemberPw()));
        
        if (loginMember.getMemberPw().equals(inputMember.getMemberPw())) {
            System.out.println("로그인 성공");
            return loginMember;
        } else {
            System.out.println("비밀번호 불일치");
            return null;
        }
    }

	@Override
	public int deleteCommentByAdmin(int commentNo) {
		// TODO Auto-generated method stub
		return mapper.deleteCommentByAdmin(commentNo);
	}

	@Override
	public int deleteBoardByAdmin(int boardNo) {
		// TODO Auto-generated method stub
		return mapper.deleteBoardByAdmin(boardNo);
	}

	/**
	 * 관리자 계정 생성
	 * @param member 생성할 관리자 정보
	 * @return 생성된 비밀번호
	 */
	// 관리자 계정 발급
		@Override
		public String createAdminAccount(Member member) {
			
			// 1. 영어 (대소문자) , 숫자도 포함 6자리 난수로 만든 비밀번호를 암호화 한 값 구하기
			String rawPw = Utility.generatePassword(); // 평문 비번
			
			// 2. 평문 비밀번호를 암호화하여 저장
			String encPw = bcrypt.encode(rawPw);
			
			// 3. member에 암호화 된 비밀 번호 세팅
			member.setMemberPw(encPw);
			
			// 4. DB에 암호화된 비밀번호가 세팅된 member를 전달하여 계정 발급
			int result = mapper.createAdminAccount(member); 
			
			int memberNo = member.getMemberNo();
			
			int createTokenInfo = mMapper.createTokenTable(memberNo);
			
			// 5. 계정 발급 정상 처리 되었다면, 발급된 (평문) 비밀번호 리턴
			if(result > 0) {
				return rawPw;
			} else {
				return null;
			}
			
		}

	/**
	 * 이메일 중복 확인
	 * @param memberEmail 확인할 이메일
	 * @return 중복된 이메일 개수 (0이면 중복 없음)
	 */
	@Override
	public int checkEmail(String memberEmail) {
		return mapper.checkEmail(memberEmail);
	}

	/**
	 * 관리자 계정 목록 조회
	 * @return 관리자 계정 목록
	 */
	@Override
	public List<Member> selectAdminList() {
		return mapper.selectAdminList();
	}


}
