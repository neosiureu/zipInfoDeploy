package com.zipinfo.project.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.zipinfo.project.common.filter.JwtAuthFilter;

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
		config.setAllowedOriginPatterns(List.of("*")); // 모든 origin 허용

		config.addAllowedHeader("*");
		config.addAllowedMethod("*");
		
	    System.out.println("씨발좀 CORS 설정 시작 - 모든 origin 허용");


		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", config);

		return source;
	}

	// Security 설정
	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
		http.cors(cors -> cors.configurationSource(corsConfigurationSource())) // 명시적으로 설정
				.csrf(csrf -> csrf.disable())

				.authorizeHttpRequests(auth -> auth
						// 공지사항 등록 (POST) 경로 수정
						.requestMatchers(HttpMethod.POST, "/api/announce/write").hasRole("ADMIN")

						// 공지사항 수정 (UPDATE) 경로 수정 (모든 write 하위 경로 포함)
						.requestMatchers(HttpMethod.POST, "/api/announce/edit/**").hasRole("ADMIN")

						// 공지사항 삭제 (DELETE) 경로 수정 (detail 하위 경로 포함)
						.requestMatchers(HttpMethod.POST, "/api/announce/detail/**").hasRole("ADMIN")

						// 관리자계정 생성
						.requestMatchers(HttpMethod.POST, "/admin/createAdminAccount").hasRole("ADMIN")

						// 조회는 모두 허용
						.requestMatchers(HttpMethod.GET, "/api/announce/**").permitAll()

						// 기타 허용 경로
						.requestMatchers("/admin/**", "/testSock/**", "/chattingSock/**").permitAll()

						// 나머지 요청 허용
						.anyRequest().permitAll()

				)
				// iframe 허용 (H2 콘솔 등)
				.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));

		return http.build();
	}
}
