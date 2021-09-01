package link4.joy.config;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class RabbitMQConfig {

    private final List<Channel> rabbitMQChannels = new LinkedList<>();
    private Connection rabbitMQConnection;

    @Autowired
    private RabbitMQProperties rabbitMQProperties;

    @PreDestroy
    void preDestroy() {
        rabbitMQChannels.forEach(ch -> {
            try {
                ch.close();
            } catch (IOException | TimeoutException e) {
                log.error("Channel#" + ch.getChannelNumber(), e);
            }
        });

        if (rabbitMQConnection != null && rabbitMQConnection.isOpen())
            try {
                rabbitMQConnection().close();
            } catch (IOException | TimeoutException e) {
                log.error("Connection#" + rabbitMQConnection.getId(), e);
            }
    }

    @Bean
    ConnectionFactory rabbitMQConnectionFactory() {
        var connectionFactory = new ConnectionFactory();
        connectionFactory.useNio();
        connectionFactory.setUsername(rabbitMQProperties.getUser());
        connectionFactory.setPassword(rabbitMQProperties.getPassword());
        connectionFactory.setHost(rabbitMQProperties.getHost());
        connectionFactory.setPort(rabbitMQProperties.getPort());
        return connectionFactory;
    }

    @Bean
    Connection rabbitMQConnection() throws IOException, TimeoutException {
        return rabbitMQConnection = rabbitMQConnectionFactory().newConnection("Tutorial");
    }

    @Bean
    ThreadLocal<Channel> rabbitMQChannel() {
        return new ThreadLocal<>() {
            @Override
            protected Channel initialValue() {
                try {
                    var channel = rabbitMQConnection().createChannel();
                    rabbitMQChannels.add(channel);
                    return channel;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
