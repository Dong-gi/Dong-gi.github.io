package link4.joy.mq;

import java.io.IOException;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MQ2 {

    public static final String EXCHANGE_NAME = "MQ2";
    public static final String QUEUE_NAME = "MQ2";
    public static final String ROUTING_KEY = QUEUE_NAME;

    @Resource(name = "rabbitMQChannel")
    private ThreadLocal<Channel> localChannel;

    @PostConstruct
    void postConstruct() throws IOException {
        var ch = localChannel.get();
        ch.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT);
        ch.queueDeclare(QUEUE_NAME, false, false, false, null);
        ch.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
        log.info("Queue({}) bound to exchange({})", QUEUE_NAME, EXCHANGE_NAME);
    }

    public void publishMessage(String msg) throws IOException {
        localChannel.get().basicPublish(EXCHANGE_NAME, ROUTING_KEY, MessageProperties.TEXT_PLAIN, msg.getBytes());
    }

}
