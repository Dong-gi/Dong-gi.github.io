package io.github.donggi.mvc.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import io.github.donggi.mvc.ws.Echo;

@ComponentScan(basePackages = "io.github.donggi.mvc.aop, io.github.donggi.mvc.controller, io.github.donggi.mvc.service, io.github.donggi.mvc.ws")
@EnableWebMvc
@EnableWebSocket
@Configuration
public class AppConfig implements WebMvcConfigurer, WebSocketConfigurer {
    @Autowired
    private Echo echo;

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("/static").setCachePeriod(31556926);
    }
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(echo, "/ws/echo").withSockJS();
    }
}
