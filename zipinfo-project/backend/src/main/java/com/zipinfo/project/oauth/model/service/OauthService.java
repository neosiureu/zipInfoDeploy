package com.zipinfo.project.oauth.model.service;

import java.util.Map;

import com.zipinfo.project.member.model.dto.Member;

public interface OauthService {

	Member loginKakao(String code);


}
