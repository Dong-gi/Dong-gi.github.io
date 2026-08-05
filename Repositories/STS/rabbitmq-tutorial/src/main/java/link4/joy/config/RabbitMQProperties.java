package link4.joy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties("link4.joy.rabbitmq")
public class RabbitMQProperties {
    private String user;
    private String password;
    private String host;
    private int port;
}
