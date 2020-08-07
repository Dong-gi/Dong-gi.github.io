package io.github.donggi.bean;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import lombok.Data;

@Data
public class Hello2 implements InitializingBean, DisposableBean {
    private String message;

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("Hello2 Bean Initialized...");
    }

    @Override
    public void destroy() throws Exception {
        System.out.println("Hello2 Bean Destroyed...");
    }
}
