package com.zipinfo.project.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /* 공통 -------------------------------------------------- */
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** 모든 경로에 적용되는 CORS 정책 */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);          // 쿠키·Auth 허용

        /* 운영/개발 도메인 허용 */
        config.addAllowedOrigin("https://cmh-board-admin.vercel.app");
        config.addAllowedOriginPattern("http://localhost:*");  // 5173, 3000 등

        /* 모든 헤더·메서드 허용 */
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /* 1) /admin/** 체인 ------------------------------------ */
    @Bean
    @Order(1)
    public SecurityFilterChain adminSecurityFilterChain(HttpSecurity http) throws Exception {

        http.securityMatcher("/admin/**")
            .cors(Customizer.withDefaults())                 // ← CorsFilter 활성화
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/admin/**",
                                     "/testSock/**",
                                     "/chattingSock/**").permitAll()
                    .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }

    /* 2) 기타 모든 요청 체인 -------------------------------- */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {

        http.securityMatcher("/**")
            .cors(Customizer.withDefaults())                 // ← CorsFilter 활성화
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
