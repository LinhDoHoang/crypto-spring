package com.crypto.crypto.config;

import com.crypto.crypto.annotation.currentuser.CurrentUserParameterResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final CurrentUserParameterResolver revolver;

    WebMvcConfig(CurrentUserParameterResolver resolver) {
        this.revolver = resolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this.revolver);
    }
}
