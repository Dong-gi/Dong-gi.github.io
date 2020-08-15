package io.github.donggi.mvc.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.AllArgsConstructor;
import lombok.Data;

@RestController
public class Controller8 {
    @Data
    @AllArgsConstructor
    static class C8Response {
        private Long userId;
        private String param;
    }
    @ResponseBody
    @RequestMapping("/8/get")
    public ResponseEntity<C8Response> get() {
        return ResponseEntity.ok(new C8Response(123L, "value"));
    }
}