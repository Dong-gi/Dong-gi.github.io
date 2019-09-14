package io.github.donggi.listener;

import java.util.logging.Logger;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ContextListener implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(ContextListener.class.getCanonicalName());
            
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("웹 앱 시작! loadOnStartup 서블릿 초기화보다 당연히 먼저 실행");
    }
/*
9월 13, 2019 10:04:38 오후 org.apache.catalina.core.StandardEngine startInternal
INFO: 서버 엔진을 시작합니다: [Apache Tomcat/9.0.17]
9월 13, 2019 10:04:39 오후 listener.ContextListener contextInitialized
INFO: 웹 앱 시작! loadOnStartup 서블릿 초기화보다 당연히 먼저 실행
 */
    
    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("웹 앱 종료!");
    }
/*
9월 13, 2019 10:04:01 오후 org.apache.catalina.core.StandardService stopInternal
INFO: 서비스 [Catalina]을(를) 중지시킵니다.
9월 13, 2019 10:04:01 오후 listener.ContextListener contextDestroyed
INFO: 웹 앱 종료!
*/
}
