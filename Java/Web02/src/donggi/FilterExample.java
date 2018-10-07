package donggi;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

public class FilterExample implements Filter {

    public static final String CLASS_NAME = FilterExample.class.getCanonicalName();

    @Override
    public void init(FilterConfig config) throws ServletException {
        System.out.println(CLASS_NAME + " >> " + config.getInitParameter("name"));
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        System.out.println(CLASS_NAME + " >> " + "사전 작업 진행");
        System.out.println(CLASS_NAME + " >> " + req.getRemoteAddr()); // 실제 타입은 HttpServletRequest
        System.out.println(CLASS_NAME + " >> " + resp.getContentType()); // 실제 타입은 HttpServletResponse
        chain.doFilter(req, resp);
        // 필터 체인에 속하는 필터들의 순서는 기본적으로 web.xml의 <filter-mapping> 순서
        // <url-pattern>이 앞서 실행되고, <servlet-name>이 나중에 실행된다.
        System.out.println(CLASS_NAME + " >> " + "사후 작업 진행");
    }

}
