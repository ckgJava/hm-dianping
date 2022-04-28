package com.hmdp.config;

import com.hmdp.interception.LoginInterception;
import com.hmdp.interception.RefreshTokenInterception;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterception()).excludePathPatterns(
                "/user/code",
                "/user/login",
                "/blog/hot",
                "/shop/**",
                "/shop/-type/**",
                "/voucher/**"
        ).order(1);
        registry.addInterceptor(new RefreshTokenInterception(stringRedisTemplate)).order(0);
    }
}
