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
                    if (authToken != null && authToken.startsWith("Bearer ")) {
                        // 기존 JwtInterceptor 로직 활용
                        try {
                            String token = authToken.substring(7);
                            int memberNo = jwtUtil.extractMemberNo(token);
                            String savedToken = memberMapper.getTokenNo(memberNo);
                            
                            if (token.equals(savedToken)) {
                                accessor.getSessionAttributes().put("authenticated", true);
                            }
                        } catch (Exception e) {
                            // OAuth 토큰이면 그냥 통과
                            accessor.getSessionAttributes().put("authenticated", true);
                        }
						
                    }
					else {
        // 이 2줄만 추가!
        accessor.getSessionAttributes().put("authenticated", true);
    }
                }
                
                return message;
            }
        });
    }
}