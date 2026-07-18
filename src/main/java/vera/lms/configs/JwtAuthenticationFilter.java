package vera.lms.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.exceptions.UnauthorizedException;
import vera.lms.models.User;
import vera.lms.repositories.UserRepository;
import vera.lms.services.AccountAccessService;
import vera.lms.utils.JwtUtils;

import java.io.IOException;
import java.time.Instant;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final AccountAccessService accountAccessService;
    private final HandlerExceptionResolver resolver;

    @Autowired
    public JwtAuthenticationFilter(
            JwtUtils jwtUtils,
            UserRepository userRepository,
            AccountAccessService accountAccessService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.accountAccessService = accountAccessService;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            User user = null;
            if (jwtUtils.isMockToken(token)) {
                user = jwtUtils.resolveMockToken(token, userRepository);
                if (user == null) {
                    throw new UnauthorizedException("Invalid mock token");
                }
            } else {
                if (!jwtUtils.validateToken(token)) {
                    throw new UnauthorizedException("Invalid or expired JWT token");
                }
                String username = jwtUtils.getUsernameFromToken(token);
                user = userRepository.findByUsername(username)
                        .orElseThrow(() -> new UnauthorizedException("User not found"));

                Instant issuedAt = jwtUtils.getIssuedAtFromToken(token);
                if (issuedAt != null && user.getUpdatedAt() != null && issuedAt.getEpochSecond() < user.getUpdatedAt().getEpochSecond()) {
                    throw new UnauthorizedException("Token was invalidated by a logout or password change");
                }
            }

            accountAccessService.ensureAccountCanAccess(user);

            if (accountAccessService.mustChangePassword(user)) {
                String uri = request.getRequestURI();
                if (!uri.equals("/api/auth/me")
                        && !uri.equals("/api/auth/change-password")
                        && !uri.equals("/api/auth/logout")) {
                    throw new ForbiddenException("Must change password first");
                }
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    user,
                    null,
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().getName().name()))
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ex) {
            resolver.resolveException(request, response, null, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
