package com.hw.manage.Config;
import com.hw.manage.Service.LoginService;
import com.hw.manage.Service.impl.LoginServiceimpl1;
import com.hw.manage.filter.JwtRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
@Configuration
@EnableWebSecurity
public class Securityconfig {



    @Autowired
    private JwtRequestFilter jwtRequestFilter;
    // 配置密码编码器
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 配置 AuthenticationManager
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 1. 禁用 CSRF
                .csrf(csrf -> csrf.disable())

                // 2. 配置认证规则
                .authorizeHttpRequests(auth -> auth
                        // 允许对登录接口的匿名访问
                        .requestMatchers("/user/login").permitAll()
                        //允许对注册接口的匿名访问
                        .requestMatchers("/user/register").permitAll()
                        // 其他所有请求都需要认证
                        .anyRequest().authenticated()
                )

                // 3. 设置 Session 管理策略为无状态 (STATELESS)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // 4. 将自定义的 JWT 过滤器添加到 Spring Security 过滤器链中
        //    放置在 UsernamePasswordAuthenticationFilter 之前
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}