package io.github.donggi.config;

import java.util.LinkedList;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import io.github.donggi.bean.Test2Bean;
import io.github.donggi.bean.Test2Bean2;
import io.github.donggi.bean.Test2Bean3;

@Profile("test2")
@Configuration
public class Test2Config {
    @Bean
    public LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }
    @Bean
    public Test2Bean test2Bean() {
        var bean = new Test2Bean();
        bean.setMsg(null);
        bean.setCost(0);
        return bean;
    }
    @Bean
    public Test2Bean2 test2Bean2() {
        var bean = new Test2Bean2();
        bean.setTest2Bean(test2Bean());
        var list = new LinkedList<Test2Bean>();
        list.add(test2Bean());
        bean.setList1(list);
        bean.setList2(new LinkedList<>());
        return bean;
    }
    @Bean
    public Test2Bean3 test2Bean3() {
        return new Test2Bean3();
    }
}