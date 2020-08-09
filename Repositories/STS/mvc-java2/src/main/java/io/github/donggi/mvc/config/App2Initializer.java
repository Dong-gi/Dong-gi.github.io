package io.github.donggi.mvc.config;

import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

public class App2Initializer extends AbstractAnnotationConfigDispatcherServletInitializer {
    @Override
    protected String getServletName() {
        return "app2";
    }
    @Override
    protected Class<?>[] getRootConfigClasses() {
        return null; // 공통 Root는 제외
    }
    @Override
    protected Class<?>[] getServletConfigClasses() {
        // rootConfig로 충분하면 null 반환
        return new Class<?>[] { App2Config.class };
    }
    @Override
    protected String[] getServletMappings() {
        return new String[] { "/app2/*" };
    }
}