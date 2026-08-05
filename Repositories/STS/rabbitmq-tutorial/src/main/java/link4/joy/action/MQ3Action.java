package link4.joy.action;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import link4.joy.mq.MQ3;
import link4.joy.res.StaticResponse;
import reactor.core.publisher.Mono;

@RestController
public class MQ3Action {

    @Autowired
    private MQ3 mq3;

    @GetMapping("/mq3/{msg}")
    public Mono<Object> newMessage(@PathVariable("msg") String msg) {
        return Mono.fromCallable(() -> {
            if (msg != null && msg.length() > 0)
                mq3.publishMessage(msg);
            return StaticResponse.OK;
        });
    }

}
