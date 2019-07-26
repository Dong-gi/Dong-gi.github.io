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

@WebFilter(
        filterName = "filter-example2",
        urlPatterns = { "/*" },
        initParams = {
                @WebInitParam(name = "name2", value = "value2")
        })
public class FilterExample2 implements Filter {

    public static final String CLASS_NAME = FilterExample2.class.getCanonicalName();

    @Override
    public void init(FilterConfig config) throws ServletException {
        System.out.println(CLASS_NAME + " >> " + config.getInitParameter("name2"));
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
