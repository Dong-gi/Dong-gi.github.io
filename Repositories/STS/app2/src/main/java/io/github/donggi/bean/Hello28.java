package io.github.donggi.bean;

import org.springframework.beans.factory.FactoryBean;
import lombok.Data;

@Data
public class Hello28 {
    private String message;

    public static class Hello28Factory implements FactoryBean<Hello28> {
        @Override
        public Hello28 getObject() throws Exception {
            var h = new Hello28();
            h.setMessage(String.format("Hello@%d", System.nanoTime()));
            return h;
        }
        @Override
        public Class<?> getObjectType() {
            return Hello28.class;
        }
        @Override
        public boolean isSingleton() {
            return false;
        }
    }
}