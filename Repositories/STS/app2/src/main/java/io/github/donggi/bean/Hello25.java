package io.github.donggi.bean;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello25 implements ApplicationContextAware {

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        log.infof("%s! You made me!", applicationContext.getBean("msg"));
    }

}
