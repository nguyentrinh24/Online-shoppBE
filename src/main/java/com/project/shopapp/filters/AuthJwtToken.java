package com.project.shopapp.filters;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class AuthJwtToken implements HandlerInterceptor {
    public static String extractToken(String authentication)
    {
        if(authentication != null && authentication.startsWith("Bearer "))
        {
            return authentication.substring(7);
        }
        return null;
    }

    public static String authHeader(HttpHeaders headers) {
        String authHeader = headers.getFirst(HttpHeaders.AUTHORIZATION);
        return authHeader;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Get token from request header
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            // Add token to response header
            response.setHeader("Authorization", token);
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // Get token from request header
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            // Add token to response header
            response.setHeader("Authorization", token);
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // Get token from request header
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            // Add token to response header
            response.setHeader("Authorization", token);
        }
    }
}
