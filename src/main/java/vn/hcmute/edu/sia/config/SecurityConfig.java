package vn.hcmute.edu.sia.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import vn.hcmute.edu.sia.security.JwtAuthenticationFilter;

@Configuration
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        public SecurityConfig(
                JwtAuthenticationFilter jwtAuthenticationFilter
        ) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(
                HttpSecurity http
        ) throws Exception {

                http
                .cors(cors -> {})
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                        "/api/auth/register",
                        "/api/auth/register/verify",
                        "/api/auth/login",
                        "/api/auth/forgot-password",
                        "/api/auth/forgot-password/verify",
                        "/api/auth/reset-password"
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
                        List.of("http://localhost:3000")
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
