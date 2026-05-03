package com.example.eyetracking.config;

import com.example.eyetracking.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/", "/about", "/contact", "/user/register").permitAll()
                .antMatchers("/dashboard", "/training/**", "/user/profile").authenticated()
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/user/login")
                .defaultSuccessUrl("/dashboard")
                .permitAll()
            .and()
            .logout()
                .logoutUrl("/user/logout")
                .logoutSuccessUrl("/")
                .permitAll();

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}