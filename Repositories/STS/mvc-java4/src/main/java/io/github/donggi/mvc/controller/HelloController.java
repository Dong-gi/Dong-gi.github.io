package io.github.donggi.mvc.controller;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class HelloController extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    @RequestMapping("/hello1")
    public void hello1() {
        throw new RuntimeException("Runtime Exception@/hello1");
    }

    @RequestMapping("/hello2")
    public void hello2() throws IOException {
        throw new IOException(new RuntimeException("Runtime Exception@/hello2"));
    }
    
    @ExceptionHandler
    public ResponseEntity<String> handle(RuntimeException e) {
        return ResponseEntity.ok().<String>body(e.getMessage());
    }
}
