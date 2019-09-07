package servlet;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Servlet implementation class LifeCycle
 */
public class LifeCycle extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(LifeCycle.class.getCanonicalName());


    /**
     * @see HttpServlet#HttpServlet()
     */
    public LifeCycle() {
        super();
        logger.info("생성자 호출");
    }
    
    @Override
    public void init(ServletConfig config) throws ServletException {
        logger.info("init 호출");
        super.init(config);
    }
    
    @Override
    public void destroy() {
        logger.info("destroy 호출");
        super.destroy();
    }
    
    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        logger.info("service 호출");
        super.service(req, res);
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    logger.info("doGet 호출");
		response.getWriter().append("Served at: ").append(request.getContextPath());
	}

	/*
	9월 07, 2019 9:03:33 오후 org.apache.catalina.startup.Catalina start
	INFO: 서버가 [971] 밀리초 내에 시작되었습니다.
	
	9월 07, 2019 9:03:40 오후 servlet.LifeCycle <init>
	INFO: 생성자 호출
	9월 07, 2019 9:03:40 오후 servlet.LifeCycle init
	INFO: init 호출
	9월 07, 2019 9:03:40 오후 servlet.LifeCycle service
	INFO: service 호출
	9월 07, 2019 9:03:40 오후 servlet.LifeCycle doGet
	INFO: doGet 호출

	9월 07, 2019 9:04:06 오후 servlet.LifeCycle service
	INFO: service 호출
	9월 07, 2019 9:04:06 오후 servlet.LifeCycle doGet
	INFO: doGet 호출

	9월 07, 2019 9:04:53 오후 org.apache.catalina.core.StandardContext reload
	INFO: 이름이 [/app1]인 컨텍스트를 다시 로드하는 작업이 시작되었습니다.
	9월 07, 2019 9:04:53 오후 servlet.LifeCycle destroy
	INFO: destroy 호출
	9월 07, 2019 9:04:53 오후 org.apache.catalina.core.StandardContext reload
	INFO: 이름이 [/app1]인 컨텍스트를 다시 로드하는 것을 완료했습니다.
	
	9월 07, 2019 9:05:16 오후 servlet.LifeCycle <init>
	INFO: 생성자 호출
	9월 07, 2019 9:05:16 오후 servlet.LifeCycle init
	INFO: init 호출
	9월 07, 2019 9:05:16 오후 servlet.LifeCycle service
	INFO: service 호출
	9월 07, 2019 9:05:16 오후 servlet.LifeCycle doGet
	INFO: doGet 호출
	 */
}
