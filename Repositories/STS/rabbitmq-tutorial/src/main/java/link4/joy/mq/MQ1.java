package link4.joy.mq;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MQ1 {

    public static final String EXCHANGE_NAME = "MQ1";
    public static final String QUEUE_NAME = "MQ1";
    private Channel channel;

    @Autowired
    private Connection c;

    @PostConstruct
    void postConstruct() throws IOException {
        channel = c.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT);
        channel.queueDeclare(QUEUE_NAME, false, false, false, null);
        channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, QUEUE_NAME);
        log.info("Queue({}) bound to exchange({})", QUEUE_NAME, EXCHANGE_NAME);
    }

    @PreDestroy
    void preDestroy() throws IOException, TimeoutException { channel.close(); }

}
