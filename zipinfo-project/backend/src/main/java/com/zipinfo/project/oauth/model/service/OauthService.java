package com.zipinfo.project.oauth.model.service;

import com.zipinfo.project.member.model.dto.Member;

public interface OauthService {

	Member loginKakao(String code);

	Member loginNaver(String token);

	int withDraw(Member loginMember);


}
