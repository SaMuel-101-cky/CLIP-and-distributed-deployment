package com.hw.manage.filter;
import com.hw.common.utils.JwtTools;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {


    private UserDetailsService userDetailsService;
    @Autowired
    public JwtRequestFilter(@Lazy UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        // 检查 Header 是否存在且以 "Bearer " 开头 JWT令牌在HTTP请求头中通常采用 Bearer 认证方案
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                 username=JwtTools.parseToken(jwt).getSubject();
            } catch (Exception e) {
                // Token 无效或已过期
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                logger.warn("JWT validation error", e);
                return ;
            }
        }

        // 如果获取到用户名，并且当前 SecurityContext 中没有认证信息
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 创建认证对象
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                // 将认证信息设置到 SecurityContext 中
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        }
        // 继续执行过滤器链
        chain.doFilter(request, response);
    }
}
