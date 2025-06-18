package com.zipinfo.project.Email.model.service;

public interface EmailService {

	String sendEmail(String string, String email);

	boolean verifyCode(String email, String authKey);

}
