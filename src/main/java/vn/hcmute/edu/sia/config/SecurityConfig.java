package vn.hcmute.edu.sia.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import vn.hcmute.edu.sia.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final String frontendOrigin;

        public SecurityConfig(
                JwtAuthenticationFilter jwtAuthenticationFilter,
                @Value("${app.frontend-origin}") String frontendOrigin
        ) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
                this.frontendOrigin = frontendOrigin;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(
                HttpSecurity http
        ) throws Exception {

                http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/register/resend-otp",
                        "/api/auth/register/verify-otp",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                        "/api/auth/forgot-password",
                        "/api/auth/forgot-password/resend-otp",
                        "/api/auth/forgot-password/verify-otp",
                        "/api/auth/reset-password",
                                "/chat-websocket",
                                "/chat-websocket/**"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form.disable())
                .httpBasic(httpBasic -> httpBasic.disable())

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

                return http.build();
        }


        @Bean
        public CorsConfigurationSource corsConfigurationSource() {

                CorsConfiguration configuration =
                        new CorsConfiguration();

                configuration.setAllowedOrigins(
                        List.of(frontendOrigin)
                );

                configuration.setAllowedMethods(
                        List.of(
                                "GET",
                                "POST",
                                "PUT",
                                "PATCH",
                                "DELETE",
                                "OPTIONS"
                        )
                );

                configuration.setAllowedHeaders(
                        List.of("*")
                );

                configuration.setAllowCredentials(true);

                UrlBasedCorsConfigurationSource source =
                        new UrlBasedCorsConfigurationSource();

                source.registerCorsConfiguration(
                        "/**",
                        configuration
                );

                return source;
        }
        
}
