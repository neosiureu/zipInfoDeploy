package com.zipinfo.project.common.config;

import com.zipinfo.project.member.model.dto.Member;
import com.zipinfo.project.member.model.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberMapper memberMapper;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            Member member = memberMapper.selectByEmail(email);
            if (member == null) {
                System.out.println("User not found with email: " + email);
                throw new UsernameNotFoundException("User not found with email: " + email);
            }

            // 권한 번호(MemberAuth) -> ROLE_ 접두어 붙여서 GrantedAuthority 생성
            String roleName;
            switch (member.getMemberAuth()) {
                case 0:
                    roleName = "ROLE_ADMIN";
                    break;
                case 1:
                    roleName = "ROLE_USER";
                    break;
                case 2:
                    roleName = "ROLE_BROKER_REQUEST";
                    break;
                case 3:
                    roleName = "ROLE_BROKER";
                    break;
                default:
                    roleName = "ROLE_GUEST";
            }

            // 로그인 및 권한 확인용 로그
            System.out.println("User found: " + member.getMemberEmail() + ", Role: " + roleName);

            GrantedAuthority authority = new SimpleGrantedAuthority(roleName);

            return new org.springframework.security.core.userdetails.User(
                    member.getMemberEmail(),
                    member.getMemberPw(), // 주의: null이면 인증 실패함
                    Collections.singletonList(authority)
            );

        } catch (PersistenceException e) {
            System.out.println("Database error occurred while loading user by email: " + email);
            throw new UsernameNotFoundException("Database error occurred", e);
        }
    }
}
