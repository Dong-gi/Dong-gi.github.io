package link4.joy.mq;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.MessageProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MQ3 {

    public static final String EXCHANGE_NAME = "MQ3";
    public static final String QUEUE_NAME = "MQ3";
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

        ch.basicConsume(QUEUE_NAME, new MQ3Consumer(ch));
    }

    public void publishMessage(String msg) throws IOException {
        var ch = localChannel.get();
        ch.confirmSelect();
        ch.basicPublish(EXCHANGE_NAME, ROUTING_KEY, MessageProperties.TEXT_PLAIN, msg.getBytes());
        try {
            if (ch.waitForConfirms(100))
                log.info("Message[{}] <<< publish ACK", msg);
            else
                log.info("Message[{}] <<< publish NACK", msg);
        } catch (InterruptedException | TimeoutException e) {
            log.info("Message[{}] <<< publish UNKNOWN", msg);
        }

    }

}
