package com.microservices.strangler.config;

import com.microservices.strangler.interceptor.StranglerInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StranglerInterceptor stranglerInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(stranglerInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/migration/**",
                        "/actuator/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**"
                );
    }
}
