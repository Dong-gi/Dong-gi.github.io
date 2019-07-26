package donggi;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

class MyResponseWrapper extends HttpServletResponseWrapper {
    private HttpServletResponse response;
    private StringWriter stringWriter;
    private PrintWriter printWriter;

    public MyResponseWrapper(HttpServletResponse response) {
        super(response);
        this.response = response;
        this.stringWriter = new StringWriter();
        this.printWriter = new PrintWriter(stringWriter);
    }
    public PrintWriter getWriter() { return printWriter; }
    public void finish() {
        try {
            response.getWriter().print(stringWriter.toString());
        } catch (IOException e) {
            e.printStackTrace();
        } 
    }
}
