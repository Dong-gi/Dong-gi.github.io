package link4.joy.action;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import link4.joy.mq.MQ6;
import link4.joy.res.StaticResponse;
import reactor.core.publisher.Mono;

@RestController
public class MQ6Action {

    @Autowired
    private MQ6 mq6;

    @GetMapping("/mq6/ok/{msg}")
    public Mono<Object> newOKMessage(@PathVariable("msg") String msg) {
        return Mono.fromCallable(() -> {
            if (msg != null && msg.length() > 0)
                mq6.publishMessageOK(msg);
            return StaticResponse.OK;
        });
    }

    @GetMapping("/mq6/ng/{msg}")
    public Mono<Object> newNGMessage(@PathVariable("msg") String msg) {
        return Mono.fromCallable(() -> {
            if (msg != null && msg.length() > 0)
                mq6.publishMessageNG(msg);
            return StaticResponse.OK;
        });
    }

}
