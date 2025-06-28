package com.project.shopapp.filters;

import com.project.shopapp.components.JwtTokenUtils;
import com.project.shopapp.models.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor

public class JwtTokenFilter extends OncePerRequestFilter{
    @Value("${api.prefix}")
    private String apiPrefix;
    private final UserDetailsService userDetailsService;
    private final JwtTokenUtils jwtTokenUtil;
    private static final Logger log = LoggerFactory.getLogger(JwtTokenFilter.class);

    @Override
    protected void doFilterInternal(@NonNull  HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if(isBypassToken(request)) {
                filterChain.doFilter(request, response); // bypass
                return;
            }
            final String authHeader = request.getHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                return;
            }
            final String token = authHeader.substring(7);
                final String phoneNumber = jwtTokenUtil.extractPhoneNumber(token);
            if (phoneNumber != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                User userDetails = (User) userDetailsService.loadUserByUsername(phoneNumber);
                if(jwtTokenUtil.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
            filterChain.doFilter(request, response);
        }catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
        }


    }
    private boolean isBypassToken(@NonNull HttpServletRequest request) {
        final List<Pair<String, String>> bypassTokens = Arrays.asList(
                Pair.of(String.format("%s/users/register", apiPrefix), "POST"),
                Pair.of(String.format("%s/users/login", apiPrefix), "POST"),
                Pair.of(String.format("%s/healthcheck/**", apiPrefix), "GET"),
                Pair.of(String.format("%s/categories**", apiPrefix), "GET"),
                Pair.of(String.format("%s/categories/**", apiPrefix), "GET"),
                Pair.of(String.format("%s/products**", apiPrefix), "GET"),
                Pair.of(String.format("%s/products/**", apiPrefix), "GET"),
                Pair.of(String.format("%s/products/featured", apiPrefix), "GET"),
                Pair.of(String.format("%s/products/images/*", apiPrefix), "GET"),
                Pair.of(String.format("%s/banners/**", apiPrefix), "GET"),
                Pair.of(String.format("%s/coupons/calculate", apiPrefix), "GET")
        );

        String requestPath = request.getServletPath();
        String requestMethod = request.getMethod();

        log.debug("isBypass? path={} method={} tokens={}", requestPath, requestMethod, bypassTokens);

        AntPathMatcher matcher = new AntPathMatcher(); // so sánh khớp pattern
        for (Pair<String, String> bypassToken : bypassTokens) {
            if (matcher.match(bypassToken.getFirst(), requestPath)
                    && requestMethod.equals(bypassToken.getSecond())) {
                return true;
            }
        }

        return false;
    }
}
