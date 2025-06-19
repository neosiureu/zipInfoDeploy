package com.zipinfo.project.email.model.service;

import java.util.Map;

public interface EmailService {

	String sendEmail(String string, String email);

	int verifyCode(Map<String, String> emailMap);

}
