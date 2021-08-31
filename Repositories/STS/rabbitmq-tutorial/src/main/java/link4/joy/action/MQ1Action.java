package link4.joy.action;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import link4.joy.res.StaticResponse;
import reactor.core.publisher.Mono;

@RestController
public class MQ1Action {

    @GetMapping("/mq1/{msg}")
    public Mono<Object> newMessage(@PathVariable("msg") String msg) { return Mono.just(StaticResponse.OK); }

}
