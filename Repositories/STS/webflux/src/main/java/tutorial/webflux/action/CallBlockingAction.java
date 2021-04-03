package tutorial.webflux.action;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@RequestMapping("/CallBlockingAction")
@Controller
public class CallBlockingAction {

    @ResponseBody
    @GetMapping("/example1")
    public Flux<String> example1() {
        return Flux.just("Hello World").publishOn(Schedulers.parallel()).map(x -> {
            log.debug("Long synchronous task");
            return x;
        });
    }

//    2021-04-03 17:22:57.394 DEBUG [reactor-http-nio-2] [HttpWebHandlerAdapter] [dc789bf6-3, L:/0:0:0:0:0:0:0:1:8080 - R:/0:0:0:0:0:0:0:1:63047] HTTP GET "/CallBlockingAction/example1"
//    2021-04-03 17:22:57.406 DEBUG [prallel-1] [CallBlockingAction] Long synchronous task
//    2021-04-03 17:22:57.406 DEBUG [prallel-1] [CharSequenceEncoder] [dc789bf6-3, L:/0:0:0:0:0:0:0:1:8080 - R:/0:0:0:0:0:0:0:1:63047] Writing "Hello World"
//    2021-04-03 17:22:57.423 DEBUG [reactor-http-nio-2] [HttpWebHandlerAdapter] [dc789bf6-3, L:/0:0:0:0:0:0:0:1:8080 - R:/0:0:0:0:0:0:0:1:63047] Completed 200 OK

    @ResponseBody
    @GetMapping("/example2")
    public Mono<String> example2() {
        return Mono.fromCallable(() -> {
            log.debug("Long synchronous task");
            return "Hello World";
        }).subscribeOn(Schedulers.newBoundedElastic(1, Integer.MAX_VALUE, "elastic"));
    }

//    2021-04-03 17:21:23.906 DEBUG [reactor-http-nio-2] [HttpWebHandlerAdapter] [dc789bf6-2, L:/0:0:0:0:0:0:0:1:8080 - R:/0:0:0:0:0:0:0:1:63047] HTTP GET "/CallBlockingAction/example2"
//    2021-04-03 17:21:23.908 DEBUG [elastic-2] [CallBlockingAction] Long synchronous task
//    2021-04-03 17:21:23.908 DEBUG [elastic-2] [CharSequenceEncoder] [dc789bf6-2, L:/0:0:0:0:0:0:0:1:8080 - R:/0:0:0:0:0:0:0:1:63047] Writing "Hello World"
//    2021-04-03 17:21:23.913 DEBUG [reactor-http-nio-2] [HttpWebHandlerAdapter] [dc789bf6-2, L:/0:0:0:0:0:0:0:1:8080 - R:/0:0:0:0:0:0:0:1:63047] Completed 200 OK

}
