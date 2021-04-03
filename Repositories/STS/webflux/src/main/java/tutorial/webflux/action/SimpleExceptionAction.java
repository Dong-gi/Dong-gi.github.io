package tutorial.webflux.action;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import reactor.core.publisher.Mono;

@RequestMapping("/SimpleExceptionAction")
@Controller
public class SimpleExceptionAction {

    @ResponseBody
    @GetMapping("/example1")
    public Mono<ResponseEntity<String>> example1() {
        return Mono.just(ResponseEntity.ok("Hello World"));
    }

    @ResponseBody
    @GetMapping("/example2")
    public Mono<ResponseEntity<String>> example2() {
        return Mono.just(ResponseEntity.status(90000).header("reason", "You bad").build());
    }

}
