package link4.joy.mq;

import java.io.IOException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;

import org.springframework.stereotype.Component;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MQ1 {

    public static final String EXCHANGE_NAME = "MQ1";
    public static final String QUEUE_NAME = "MQ1";
    public static final String ROUTING_KEY = QUEUE_NAME;

    @Resource(name = "rabbitMQChannel")
    private ThreadLocal<Channel> localChannel;

    @PostConstruct
    void postConstruct() throws IOException {
        var ch = localChannel.get();
        ch.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT);
        ch.queueDeclare(QUEUE_NAME, false, false, false, null);
        ch.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
        log.info("Queue({}) bound to exchange({}) with {}", QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
    }

}
