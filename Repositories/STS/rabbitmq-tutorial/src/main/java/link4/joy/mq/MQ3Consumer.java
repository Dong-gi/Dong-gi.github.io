package link4.joy.mq;

import java.io.IOException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import lombok.extern.slf4j.Slf4j;

import com.rabbitmq.client.AMQP.BasicProperties;

@Slf4j
public class MQ3Consumer extends DefaultConsumer {

    public MQ3Consumer(Channel channel) { super(channel); }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, BasicProperties properties, byte[] body)
        throws IOException {
        var deliveryTag = envelope.getDeliveryTag();
        log.info(new StringBuilder().append("consumerTag=").append(consumerTag).append(", deliveryTag=")
            .append(deliveryTag).append(", routingKey=").append(envelope.getRoutingKey()).append(", body=")
            .append(new String(body)).toString());
        getChannel().basicAck(deliveryTag, false);
    }
}
