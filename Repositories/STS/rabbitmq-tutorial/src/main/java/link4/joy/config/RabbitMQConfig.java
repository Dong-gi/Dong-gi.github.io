package link4.joy.config;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

@Configuration
public class RabbitMQConfig {

    private Connection rabbitMQConnection;

    @Autowired
    private RabbitMQProperties rabbitMQProperties;

    @PreDestroy
    void preDestroy() throws IOException, TimeoutException {
        if (rabbitMQConnection != null)
            rabbitMQConnection().close();
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
}
