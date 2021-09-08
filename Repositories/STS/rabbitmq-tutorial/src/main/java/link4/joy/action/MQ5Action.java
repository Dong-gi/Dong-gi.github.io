package link4.joy.action;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import link4.joy.mq.MQ5;
import link4.joy.res.StaticResponse;
import reactor.core.publisher.Mono;

@RestController
public class MQ5Action {

    @Autowired
    private MQ5 mq5;

    @GetMapping("/mq5/{msg}")
    public Mono<Object> newMessage(@PathVariable("msg") String msg) {
        return Mono.fromCallable(() -> {
            if (msg != null && msg.length() > 0)
                mq5.publishMessage(msg);
            return StaticResponse.OK;
        });
    }

}
