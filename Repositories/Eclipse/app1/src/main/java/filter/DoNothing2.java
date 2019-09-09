package filter;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.annotation.WebInitParam;


@WebFilter(
        filterName = "filter-doNothing2",
        urlPatterns = { "/*" },
        initParams = {
                @WebInitParam(name = "name", value = "기동")
        }) 
public class DoNothing2 implements Filter {
    private static final Logger logger = Logger.getLogger(DoNothing2.class.getCanonicalName());

    @Override
    public void init(FilterConfig config) throws ServletException {
        logger.info("필터 초기화 : " + config.getInitParameter("name"));
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        logger.info("사전 작업 진행");
        logger.info(req.getRemoteAddr());   // 실제 타입은 HttpServletRequest
        logger.info(resp.getContentType()); // 실제 타입은 HttpServletResponse
        chain.doFilter(req, resp);          // 필터 체인에 속하는 필터들의 순서는 기본적으로 web.xml의 <filter-mapping> 순서
        logger.info("사후 작업 진행");
    } 
    
    /*
9월 09, 2019 10:24:23 오후 filter.DoNothing doFilter
INFO: 사전 작업 진행
9월 09, 2019 10:24:23 오후 filter.DoNothing doFilter
INFO: 0:0:0:0:0:0:0:1
9월 09, 2019 10:24:23 오후 filter.DoNothing doFilter
INFO: null
9월 09, 2019 10:24:23 오후 filter.DoNothing2 doFilter
INFO: 사전 작업 진행
9월 09, 2019 10:24:23 오후 filter.DoNothing2 doFilter
INFO: 0:0:0:0:0:0:0:1
9월 09, 2019 10:24:23 오후 filter.DoNothing2 doFilter
INFO: null
9월 09, 2019 10:24:24 오후 filter.DoNothing2 doFilter
INFO: 사후 작업 진행
9월 09, 2019 10:24:24 오후 filter.DoNothing doFilter
INFO: 사후 작업 진행
     */

}
