package donggi;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

public class MyRequestWrapper extends HttpServletRequestWrapper {
    private HttpServletRequest request;
    
    public MyRequestWrapper(HttpServletRequest request) {
        super(request);
        this.request = request;
    }
    
    public String getParameter(String name) {
        return request.getParameter(name).toLowerCase();
    }
}
