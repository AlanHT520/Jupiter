package net.alan.jupiter.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.constant.CommonConstants;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.infrastructure.security.JwtTokenProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CounselorRepository counselorRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
            String userId = jwtTokenProvider.getUserIdFromToken(token);

            var userOpt = counselorRepository.findById(userId);
            if (userOpt.isEmpty()) {
                log.warn("Token 对应的用户不存在: userId={}", userId);
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "用户不存在");
                return;
            }
            long tokenVer = jwtTokenProvider.getTokenVersionFromToken(token);
            long curVer = userOpt.get().getTokenVersion() != null
                    ? userOpt.get().getTokenVersion() : 0L;
            if (tokenVer < curVer) {
                log.warn("Token 版本过期: userId={}, tokenVer={}, curVer={}", userId, tokenVer, curVer);
                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED, "凭证已失效，请重新登录");
                return;
            }

            // 角色以数据库为准（避免降权后旧 token 仍保留 ROLE_ADMIN 直到过期）
            String dbRole = userOpt.get().getRole();
            String authority = dbRole == null || dbRole.isBlank()
                    ? "ROLE_COUNSELOR"
                    : (dbRole.startsWith("ROLE_") ? dbRole : "ROLE_" + dbRole);

            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private void writeJson(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":" + status + ",\"message\":\"" + message + "\"}");
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(CommonConstants.TOKEN_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(CommonConstants.TOKEN_PREFIX)) {
            return bearerToken.substring(CommonConstants.TOKEN_PREFIX.length());
        }
        return null;
    }
}