package request;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class RequestWrapper extends HttpServletRequestWrapper {
    private HttpServletRequest request;
    
    public RequestWrapper(HttpServletRequest request) {
        super(request);
        this.request = request;
    }
    
    /* 비즈니스 로직 */
} 