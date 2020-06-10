package io.github.donggi.bean;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.springframework.beans.factory.support.MethodReplacer;

import lombok.extern.jbosslog.JBossLog;

@JBossLog
public class Hello22Replacement implements MethodReplacer {

    @Override
    public Object reimplement(Object obj, Method method, Object[] args) throws Throwable {
        var hello22 = (Hello22) obj;
        log.infof("(%s).%s(%s)", hello22, method.getName(), Arrays.toString(args));
        return String.format("%s * %d", hello22.getMessage(), args[0]);
    }
    
}
