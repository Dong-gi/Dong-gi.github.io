package io.github.donggi.bean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello27 {
    public static class HelloProcessor implements BeanPostProcessor {
        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            log.infof("Before init callbacks of %s", beanName);
            return bean;
        }
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            log.infof("After init callbacks of %s", beanName);
            return bean;
        }
    }
}