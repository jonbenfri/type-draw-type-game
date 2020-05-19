package net.czedik.hermann.tdt;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/g/{gameId:\\w+}").setViewName("forward:/");
        registry.addViewController("/new").setViewName("forward:/");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // very long caching for static (hashed files)
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/public/static/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS));

        // no caching (browser needs to re-validate) for all the rest
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/public/")
                .setCacheControl(CacheControl.noCache());
    }
}
