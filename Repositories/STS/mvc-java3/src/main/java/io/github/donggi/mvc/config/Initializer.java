package io.github.donggi.mvc.config;

import javax.servlet.ServletContext;

import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

public class Initializer implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext servletCxt) {
        // WebApplicationContext 로드
        var ac = new AnnotationConfigWebApplicationContext();
        ac.setServletContext(servletCxt);
        ac.register(AppConfig.class);
        ac.refresh();

        // DispatcherServlet 등록
        var dispatcher = new DispatcherServlet(ac);
        var registration = servletCxt.addServlet("app", dispatcher);
        registration.setLoadOnStartup(1);
        registration.addMapping("/*");
    }
}