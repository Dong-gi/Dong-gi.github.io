package link4.joy.mq;

import java.io.IOException;
import java.util.HashMap;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP.BasicProperties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MQ6 {

    public static final String EXCHANGE_NAME = "MQ6";
    public static final String QUEUE_NAME = "MQ6";
    public static final String ROUTING_KEY = QUEUE_NAME;

    @Resource(name = "rabbitMQChannel")
    private ThreadLocal<Channel> localChannel;

    @PostConstruct
    void postConstruct() throws IOException {
        var ch = localChannel.get();
        ch.exchangeDelete(EXCHANGE_NAME);
        ch.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.HEADERS);
        ch.queueDeclare(QUEUE_NAME, false, false, false, null);

        var bindArgs = new HashMap<String, Object>();
        bindArgs.put("x-match", "any");
        bindArgs.put("from", "wiz");
        ch.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY, bindArgs);
        log.info("Queue({}) bound to exchange({}) with {}", QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);

        ch.basicConsume(QUEUE_NAME, new SimpleConsumer(ch));
    }

    public void publishMessageOK(String msg) throws IOException {
        var headers = new HashMap<String, Object>();
        headers.put("from", "wiz");
        localChannel.get().basicPublish(
            EXCHANGE_NAME,
            ROUTING_KEY,
            new BasicProperties("text/plain", null, headers, 1, 0, null, null, null, null, null, null, null, null,
                null),
            msg.getBytes());
    }

    public void publishMessageNG(String msg) throws IOException {
        var headers = new HashMap<String, Object>();
        headers.put("from", "ziw");
        localChannel.get().basicPublish(
            EXCHANGE_NAME,
            ROUTING_KEY,
            new BasicProperties("text/plain", null, headers, 1, 0, null, null, null, null, null, null, null, null,
                null),
            msg.getBytes());
    }

}
