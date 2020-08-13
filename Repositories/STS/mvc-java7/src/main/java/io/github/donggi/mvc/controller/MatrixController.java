package io.github.donggi.mvc.controller;

import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.MatrixVariable;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/mat/**")
@Slf4j
public class MatrixController {
    @GetMapping("/1/{path1}")
    @ResponseStatus(HttpStatus.OK)
    public void mat1(HttpServletRequest request, @PathVariable String path1, @MatrixVariable(defaultValue = "") String q) {
        log.debug("uri : " + request.getRequestURI());
        log.debug("path1 : " + path1 + ", q : " + q);
/*
09:15:56.061 [http-nio-8080-exec-13] DEBUG i.g.d.m.controller.MatrixController - uri : /mvc-java7/app/mat/1/asdf
09:15:56.061 [http-nio-8080-exec-13] DEBUG i.g.d.m.controller.MatrixController - path1 : asdf, q : 

09:16:23.660 [http-nio-8080-exec-1] DEBUG i.g.d.m.controller.MatrixController - uri : /mvc-java7/app/mat/1/asdf;q=a,b,c,d
09:16:23.660 [http-nio-8080-exec-1] DEBUG i.g.d.m.controller.MatrixController - path1 : asdf, q : a,b,c,d
 */
    }
    
    @GetMapping("/2/{path1}/{path2}")
    @ResponseStatus(HttpStatus.OK)
    public void mat2(HttpServletRequest request,
            @PathVariable String path1, @MatrixVariable(name = "q", pathVar = "path1") String q1,
            @PathVariable String path2, @MatrixVariable(name = "q", pathVar = "path2") String q2) {
        log.debug("uri : " + request.getRequestURI());
        log.debug("path1 : " + path1 + ", q1 : " + q1);
        log.debug("path2 : " + path2 + ", q2 : " + q2);
/*
09:21:45.531 [http-nio-8080-exec-3] DEBUG i.g.d.m.controller.MatrixController - uri : /mvc-java7/app/mat/2/abc;p=abc_p;q=abc_q/123;p=123_p;q=123_q
09:21:45.531 [http-nio-8080-exec-3] DEBUG i.g.d.m.controller.MatrixController - path1 : abc, q1 : abc_q
09:21:45.531 [http-nio-8080-exec-3] DEBUG i.g.d.m.controller.MatrixController - path2 : 123, q2 : 123_q
 */
    }
    
    @GetMapping("/3/{path1}/{path2}")
    @ResponseStatus(HttpStatus.OK)
    public void mat3(HttpServletRequest request,
            @MatrixVariable MultiValueMap<String, String> allMat,
            @MatrixVariable(pathVar = "path1") MultiValueMap<String, String> path1Mat) {
        log.debug("uri : " + request.getRequestURI());
        log.debug("allMat : ");
        allMat.forEach((k, v) -> log.debug("  key : {}, value : {}", k, v));
        log.debug("path1Mat : ");
        path1Mat.forEach((k, v) -> log.debug("  key : {}, value : {}", k, v));
/*
09:25:22.467 [http-nio-8080-exec-2] DEBUG i.g.d.m.controller.MatrixController - uri : /mvc-java7/app/mat/3/abc;p=abc_p;q=abc_q/123;p=123_p;q=123_q
09:25:22.467 [http-nio-8080-exec-2] DEBUG i.g.d.m.controller.MatrixController - allMat : 
09:25:22.467 [http-nio-8080-exec-2] DEBUG i.g.d.m.controller.MatrixController -   key : p, value : [123_p, abc_p]
09:25:22.471 [http-nio-8080-exec-2] DEBUG i.g.d.m.controller.MatrixController -   key : q, value : [123_q, abc_q]
09:25:22.471 [http-nio-8080-exec-2] DEBUG i.g.d.m.controller.MatrixController - path1Mat : 
09:25:22.471 [http-nio-8080-exec-2] DEBUG i.g.d.m.controller.MatrixController -   key : p, value : [abc_p]
09:25:22.471 [http-nio-8080-exec-2] DEBUG i.g.d.m.controller.MatrixController -   key : q, value : [abc_q]
 */
    }
}
