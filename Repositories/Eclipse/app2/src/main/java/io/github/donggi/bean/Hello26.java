package io.github.donggi.bean;

import org.springframework.beans.factory.BeanNameAware;
import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello26 implements BeanNameAware {
    @Override
    public void setBeanName(String name) {
        log.infof("You named me %s, thanks!", name);
    }
}
