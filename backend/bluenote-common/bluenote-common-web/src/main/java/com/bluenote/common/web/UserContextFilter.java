package com.bluenote.common.web;

import com.bluenote.common.security.SecurityHeaders;
import com.bluenote.common.security.UserContext;
import com.bluenote.common.security.UserContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        UserContextHolder.set(new UserContext(
                request.getHeader(SecurityHeaders.USER_ID),
                request.getHeader(SecurityHeaders.DEVICE_ID),
                request.getHeader(SecurityHeaders.SESSION_ID)
        ));

        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}
