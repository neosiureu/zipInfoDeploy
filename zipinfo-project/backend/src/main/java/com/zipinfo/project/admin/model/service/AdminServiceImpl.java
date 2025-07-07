package com.zipinfo.project.admin.model.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zipinfo.project.admin.model.mapper.AdminMapper;
import com.zipinfo.project.member.model.dto.Member;

import lombok.RequiredArgsConstructor;


@Transactional(rollbackFor = Exception.class)
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminMapper mapper;

    /**
     * 관리자 로그인 처리
     * 
     * @param inputMember 입력받은 회원 정보 (이메일, 비밀번호)
     * @return 로그인 성공 시 회원 정보 반환, 실패 시 null 반환
     */
    @Override
    public Member login(Member inputMember) {
        // 이메일로 회원 조회
        Member loginMember = mapper.login(inputMember.getMemberEmail());

        // 회원이 없으면 null 반환
        if (loginMember == null) {
            return null;
        }

        // 비밀번호 일치 여부 확인 (단순 문자열 비교 - 실제 운영 시 암호화 필요)
        if (loginMember.getMemberPw().equals(inputMember.getMemberPw())) {
            return loginMember;
        } else {
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
}
