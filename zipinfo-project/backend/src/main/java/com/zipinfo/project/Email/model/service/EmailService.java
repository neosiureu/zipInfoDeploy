package com.zipinfo.project.Email.model.service;

import java.util.Map;

public interface EmailService {

	String sendEmail(String string, String email);

	int verifyCode(Map<String, String> emailMap);

}
