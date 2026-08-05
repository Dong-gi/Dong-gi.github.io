package io.github.donggi.mvc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import io.github.donggi.mvc.handler.Handler1;
import io.github.donggi.mvc.handler.Handler2;

import static org.springframework.web.servlet.function.RequestPredicates.*;
import static org.springframework.web.servlet.function.RouterFunctions.route;

@ComponentScan(basePackages = "io.github.donggi.mvc.handler, io.github.donggi.mvc.service")
@EnableWebMvc
@Configuration
public class AppConfig implements WebMvcConfigurer {
    @Autowired Handler1 h1;
    @Autowired Handler2 h2;

    @Bean
    public RouterFunction<ServerResponse> router() {
        return route()
                .GET("/1/get", accept(MediaType.ALL), h1::get)
                .POST("/1/post", h1::post)
                .GET("/2/get", h2::get)
                .POST("/2/post", h2::post)
                .build();
    }
}
