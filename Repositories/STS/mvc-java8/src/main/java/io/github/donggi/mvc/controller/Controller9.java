package io.github.donggi.mvc.controller;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
public class Controller9 {
    @ModelAttribute
    public void setModel1(@PathVariable(required = false) String msg, Model m) {
        m.addAttribute("msg", msg);
    }
    @ModelAttribute(name = "num")
    public int setModel2() {
        return 123;
    }

    @ResponseStatus(HttpStatus.OK)
    @RequestMapping("/9/get/{msg}")
    public void get(Model m) {
        log.debug("Model : " + m);
/*
15:46:56.935 [http-nio-8080-exec-4] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java8/app/9/get/Hello%20World", parameters={}, headers={masked} in DispatcherServlet 'app'
15:46:57.006 [http-nio-8080-exec-4] DEBUG i.g.d.mvc.controller.Controller9 - Model : {num=123, msg=Hello World}
 */
    }
}