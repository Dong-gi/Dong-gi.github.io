package link4.joy.action;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import link4.joy.res.StaticResponse;
import reactor.core.publisher.Mono;

@RestController
public class MQ1Action {

    @GetMapping("/q89q347gtb89q3g47btv89q7b3g4t7vgeqwo8rgvtpqw8e7/update/{sha}")
    public Mono<Object> newMessage(@PathVariable("sha") String sha) {
        return Mono.just(StaticResponse.OK); }
}
