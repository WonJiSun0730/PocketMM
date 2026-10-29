package com.example.projectmm.infrastructure.security;

import com.example.projectmm.domain.character.CharacterRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.util.Map;

/** Legacy character conversations have no separate member field: the character owner owns them. */
@Configuration
public class PrivateDataAccessConfiguration implements WebMvcConfigurer {
    private final CharacterRepository characters;
    private final TransactionTemplate read;
    public PrivateDataAccessConfiguration(CharacterRepository characters, PlatformTransactionManager transactions) {
        this.characters = characters;
        read = new TransactionTemplate(transactions);
        read.setReadOnly(true);
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth == null || !auth.isAuthenticated() || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
                @SuppressWarnings("unchecked")
                var path = (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (path != null && path.containsKey("characterId")) {
                    Long id;
                    try { id = Long.valueOf(path.get("characterId")); }
                    catch (NumberFormatException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST); }
                    boolean owns = Boolean.TRUE.equals(read.execute(status -> characters.findById(id)
                            .filter(c -> c.getOwner() != null && auth.getName().equals(c.getOwner().getLoginId()))
                            .isPresent()));
                    if (!owns) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
                }
                response.setHeader("Cache-Control", "no-store");
                return true;
            }
        }).addPathPatterns("/api/chat/**", "/api/worlds/*/characters/*/**");
    }
}
