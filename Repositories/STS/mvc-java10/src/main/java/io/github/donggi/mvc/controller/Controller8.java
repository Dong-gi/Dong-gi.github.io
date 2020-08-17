package io.github.donggi.mvc.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Controller8 {
    @CrossOrigin(origins = "*")
    @RequestMapping(value = "/8/get1")
    public String get1() {
        return "Hello World!";
    }
/*
xhr = new XMLHttpRequest()
xhr.open('GET', 'http://localhost:8080/mvc-java10/app/8/get1')
xhr.send()

xhr.response == "Hello World!"
 */
}