package link4.joy.mq;

import java.io.IOException;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FallbackMQ {

    public static final String EXCHANGE_NAME = "FallbackMQ";
    public static final String QUEUE_NAME = "FallbackMQ";
    public static final String ROUTING_KEY = "";

    @Resource(name = "rabbitMQChannel")
    private ThreadLocal<Channel> localChannel;

    @PostConstruct
    void postConstruct() throws IOException {
        var ch = localChannel.get();
        ch.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT);
        ch.queueDeclare(QUEUE_NAME, false, false, false, null);
        ch.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
        log.info("Queue({}) bound to exchange({}) with {}", QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);

        ch.basicConsume(QUEUE_NAME, new FallbackConsumer(ch));
    }

}
