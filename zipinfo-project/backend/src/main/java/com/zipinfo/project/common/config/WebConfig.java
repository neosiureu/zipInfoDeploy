package com.zipinfo.project.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // CORS 설정
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")  // 모든 경로에 대해
                .allowedOrigins("http://localhost:5173")  // 프론트엔드 주소
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowCredentials(true);
    }

    // 업로드된 파일을 정적 리소스로 제공하기 위한 매핑
 // WebConfig 예시
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 기존 동네 이미지 매핑 유지
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:///C:/uploadFiles/boardImg/");

        // 공지사항 이미지 매핑 추가
        registry.addResourceHandler("/images/announceImg/**")
                .addResourceLocations("file:///C:/uploadFiles/announceImg/");
        
        // 광고 배너 이미지
        registry.addResourceHandler("/images/advertiseImg/**")

        		.addResourceLocations("file:///C:/uploadFiles/advertiseImg/");
        
        // 분양 이미지
        registry.addResourceHandler("/images/saleImg/thumbnail/**")
        		.addResourceLocations("file:///C:/uploadFiles/saleImg/thumbnail/");
    }

        .addResourceLocations("file:///C:/uploadFiles/advertiseImg/");
        
        registry.addResourceHandler("/message/messageFile/**")
        .addResourceLocations("file:///C:/uploadFiles/message/");
}


}
