package io.github.donggi.controller;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.github.donggi.response.HelloResponse;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
public class HelloControllerTest {

    private WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:18888").build();
    
    @Test
    public void helloTest() {
        var result = client.get().uri("/hello").exchange().expectBody(HelloResponse.class).returnResult();
        System.out.println(result);
    }
    
}
