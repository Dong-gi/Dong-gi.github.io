package donggi;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;

@WebFilter(filterName = "hello-filter2", urlPatterns = { "/*" }, initParams = {
        @WebInitParam(name = "log_file_name", value = "C:/logs/hello_filter2.log") })
public class HelloFilter2 implements Filter {

    public static final String CLASS_NAME = HelloFilter2.class.getCanonicalName();

    @Override
    public void init(FilterConfig config) throws ServletException {
        System.out.println(CLASS_NAME + " >> " + config.getInitParameter("log_file_name"));
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        System.out.println(CLASS_NAME + " >> " + "사전 작업 진행");
        req.setCharacterEncoding("utf-8");
        resp.setCharacterEncoding("utf-8");
        chain.doFilter(req, resp);
        System.out.println(CLASS_NAME + " >> " + "사후 작업 진행");
    }

}
