package io.github.donggi.mvc.config;

import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class App1Initializer extends AbstractAnnotationConfigDispatcherServletInitializer {
    @Override
    protected String getServletName() {
        return "app1";
    }
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[] { RootConfig.class };
    }
    @Override
    protected Class<?>[] getServletConfigClasses() {
        // rootConfig로 충분하면 null 반환
        return new Class<?>[] { App1Config.class };
    }
    @Override
    protected String[] getServletMappings() {
        return new String[] { "/app1/*" };
    }
}