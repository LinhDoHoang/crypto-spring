package com.crypto.crypto.annotation.currentuser;

import com.crypto.crypto.entities.UsersEntity;
import com.crypto.crypto.feature.auth.AuthException;
import com.crypto.crypto.feature.users.UsersRepository;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserParameterResolver implements HandlerMethodArgumentResolver {
    private final UsersRepository usersRepository;

    CurrentUserParameterResolver(UsersRepository usersRepository) {
        this.usersRepository = usersRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && UsersEntity.class.isAssignableFrom(
                parameter.getParameterType()
        );
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)
                || !authentication.isAuthenticated()) {
            throw new AuthException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        String subject = jwtAuthenticationToken.getToken().getSubject();

        Long userId = Long.valueOf(subject);

        return usersRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getEnabled()))
                .orElseThrow(() ->
                    new AuthException(
                            HttpStatus.UNAUTHORIZED,
                            "User is unavailable"
                    )
                );
    }
}
