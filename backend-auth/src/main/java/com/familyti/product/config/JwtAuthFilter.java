package com.familyti.product.config;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.familyti.product.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    public static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/users/register"
    };

    private static final Set<String> PUBLIC_PATH_SET = Set.of(PUBLIC_PATHS);

    private final TokenService tokenService;
    private final UserAccountRepository userRepository;
    private final SecurityErrorHandler securityErrorHandler;

    public JwtAuthFilter(TokenService tokenService,
                         UserAccountRepository userRepository,
                         SecurityErrorHandler securityErrorHandler) {
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.securityErrorHandler = securityErrorHandler;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = this.recoverToken(request);
        if (token != null) {
            try {
                String login = tokenService.validateToken(token);
                UserDetails user = userRepository.findByEmail(login);

                if (user == null) {
                    abort(request, response, new BadCredentialsException("Token subject no longer exists."));
                    return;
                }

                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (TokenExpiredException e) {
                abort(request, response, new BadCredentialsException("Expired token.", e));
                return;
            } catch (JWTVerificationException e) {
                abort(request, response, new BadCredentialsException("Invalid token.", e));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return PUBLIC_PATH_SET.contains(request.getRequestURI());
    }

    private void abort(HttpServletRequest request,
                       HttpServletResponse response,
                       AuthenticationException exception) throws IOException {
        SecurityContextHolder.clearContext();
        securityErrorHandler.commence(request, response, exception);
    }

    private String recoverToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}