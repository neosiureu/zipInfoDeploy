package com.zipinfo.project.webSocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		// 프론트에서 /ws 또는 /api/ws 어느 쪽으로 와도 연결되도록 둘 다 등록
		registry.addEndpoint("/ws", "/api/ws").setAllowedOriginPatterns("*") // 필요시 "https://zipinfo.site" 등으로 제한
				.withSockJS(); // SockJS fallback (iframe.html 등) 활성화
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableSimpleBroker("/topic");
		config.setApplicationDestinationPrefixes("/app");
	}
}