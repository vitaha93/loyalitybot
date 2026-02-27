package org.jume.loyalitybot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static files from /static/
        registry.addResourceHandler("/admin/css/**")
                .addResourceLocations("classpath:/static/admin/css/");

        registry.addResourceHandler("/admin/js/**")
                .addResourceLocations("classpath:/static/admin/js/");

        // Serve webjars
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
