package com.limiteddeals.order.config;

import com.limiteddeals.order.service.StockStrategy;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** ?strategy=atomic 처럼 소문자로 와도 StockStrategy로 바꿔준다. 모르는 값이면 400. */
    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, StockStrategy.class, s -> StockStrategy.valueOf(s.trim().toUpperCase()));
    }
}
