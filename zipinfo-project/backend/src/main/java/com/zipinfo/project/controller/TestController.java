package com.zipinfo.project.controller;

import org.springframework.web.bind.annotation.GetMapping;

public class TestController {
	
	@GetMapping("/catch/request")
	public int loginSensor() {
		return 1;
	}
}
