package link4.joy.mq;

import java.io.IOException;
import java.util.HashMap;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MQ4 {

    public static final String EXCHANGE_NAME = "MQ4";
    public static final String QUEUE_NAME = "MQ4";
    public static final String ROUTING_KEY = QUEUE_NAME;

    @Resource(name = "rabbitMQChannel")
    private ThreadLocal<Channel> localChannel;

    @PostConstruct
    void postConstruct() throws IOException {
        var ch = localChannel.get();
        var args = new HashMap<String, Object>();
        args.put("alternate-exchange", FallbackMQ.EXCHANGE_NAME);
        ch.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT, false, false, args);
        ch.queueDeclare(QUEUE_NAME, false, false, false, null);
        ch.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
        log.info("Queue({}) bound to exchange({}) with {}", QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);

        ch.basicConsume(QUEUE_NAME, new SimpleConsumer(ch));
    }

    public void publishMessage(String msg) throws IOException {
        localChannel.get().basicPublish(EXCHANGE_NAME, "NON_REGISTERED_ROUTING_KEY", MessageProperties.TEXT_PLAIN,
            msg.getBytes());
    }

}
