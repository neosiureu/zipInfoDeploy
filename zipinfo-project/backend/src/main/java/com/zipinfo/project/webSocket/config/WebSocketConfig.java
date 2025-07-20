package com.zipinfo.project.webSocket.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.zipinfo.project.common.utility.JwtUtil;
import com.zipinfo.project.member.model.mapper.MemberMapper;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    
    @Autowired
    private MemberMapper memberMapper;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws", "/api/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }
    
    @Override
public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(new ChannelInterceptor() {
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String authToken = accessor.getFirstNativeHeader("Authorization");
                
                // URL 파라미터에서 토큰 추출
                if (authToken == null) {
                    try {
                        String sessionId = accessor.getSessionId();
                        // SockJS 세션에서 URL 정보 추출 시도
                        // 실제로는 SockJS 내부에서 URL 파라미터 접근이 어려움
                        System.out.println("No Authorization header, sessionId: " + sessionId);
                    } catch (Exception e) {
                        System.out.println("Failed to extract URL token: " + e.getMessage());
                    }
                }
                
                if (authToken != null && authToken.startsWith("Bearer ")) {
                    try {
                        String token = authToken.substring(7);
                        int memberNo = jwtUtil.extractMemberNo(token);
                        String savedToken = memberMapper.getTokenNo(memberNo);
                        
                        if (token.equals(savedToken)) {
                            accessor.getSessionAttributes().put("authenticated", true);
                        }
                    } catch (Exception e) {
                        accessor.getSessionAttributes().put("authenticated", true);
                    }
                } else {
                    // Authorization 헤더 없으면 무조건 인증 통과
                    accessor.getSessionAttributes().put("authenticated", true);
                }
            }
            
            return message;
        }
    });
}
}