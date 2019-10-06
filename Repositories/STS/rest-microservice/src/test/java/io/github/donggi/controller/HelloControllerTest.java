package io.github.donggi.controller;

import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.donggi.response.HelloResponse;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class HelloControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    public void helloTest1() {
        var result = restTemplate.getForObject("/hello", HelloResponse.class);
        System.out.println(result);
    }

    @Test
    public void helloTest2() throws IOException {
        var json = restTemplate.getForObject("/hello", String.class);
        System.out.println(new ObjectMapper().readTree(json).get("message"));
    }
    
}
