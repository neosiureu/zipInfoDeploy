package com.zipinfo.project.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    // 비밀번호 암호화 빈
    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // CORS 설정
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://cmh-announce-admin.vercel.app"
        ));
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    // Security 설정
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // 공지사항 등록/수정/삭제는 관리자만 가능
                .requestMatchers(HttpMethod.POST, "/api/announce").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/announce/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/announce/**").hasRole("ADMIN")

                // 공지사항 조회는 모두 허용
                .requestMatchers(HttpMethod.GET, "/api/announce/**").permitAll()

                // 관리자 기타 경로 허용
                .requestMatchers("/admin/**", "/testSock/**", "/chattingSock/**").permitAll()

                // 나머지 모든 요청 허용
                .anyRequest().permitAll()
            )
            // iframe 허용 (H2 콘솔 등)
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

        return http.build();
    }
}
