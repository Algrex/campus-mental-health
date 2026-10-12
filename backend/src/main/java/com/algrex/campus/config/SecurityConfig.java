package com.algrex.campus.config;

import com.algrex.campus.demo.DemoAccounts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
        .authorizeHttpRequests(requests -> requests
            .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/v1/system/info", "/api/v1/auth/session").permitAll()
            .requestMatchers("/api/v1/auth/login").permitAll()
            .requestMatchers("/api/v1/**").authenticated()
            .anyRequest().denyAll())
        .formLogin(login -> login.loginProcessingUrl("/api/v1/auth/login")
            .successHandler((request, response, authentication) -> {
              response.setContentType("application/json;charset=UTF-8");
              response.getWriter().write("{\"ok\":true}");
            })
            .failureHandler((request, response, exception) -> {
              response.setStatus(401);
              response.setContentType("application/json;charset=UTF-8");
              response.getWriter().write("{\"message\":\"账号或密码不正确\"}");
            }))
        .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
            .logoutSuccessHandler((request, response, authentication) -> response.setStatus(204)))
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) -> {
              response.setStatus(401);
              response.setContentType("application/json;charset=UTF-8");
              response.getWriter().write("{\"message\":\"请先登录\"}");
            })
            .accessDeniedHandler((request, response, exception) -> {
              response.setStatus(403);
              response.setContentType("application/json;charset=UTF-8");
              response.getWriter().write("{\"message\":\"操作未获授权，请刷新页面后重试\"}");
            }))
        .build();
  }

  @Bean
  UserDetailsService userDetailsService(DemoAccounts accounts, PasswordEncoder encoder,
      @Value("${app.demo.password:Campus2026!}") String password) {
    return new InMemoryUserDetailsManager(accounts.all().stream()
        .map(account -> User.withUsername(account.username()).password(encoder.encode(password))
            .roles(account.role()).build()).toList());
  }
}
