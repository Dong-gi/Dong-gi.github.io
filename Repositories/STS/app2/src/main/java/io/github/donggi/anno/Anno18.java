package io.github.donggi.anno;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import lombok.Data;

@Configuration
@PropertySource(value = "src/main/resource/anno18.properties", encoding = "UTF-8", ignoreResourceNotFound = true)
class Anno18Config {}

@Data
@Component
public class Anno18 {
    @Value("${anno18.sayHello}")
    private boolean sayHello;
    @Value("${anno18.msg}")
    private String msg;
    @Value("${anno18.noMsg:default msg : 안녕}")
    private String noMsg;
    @Value("${anno18.num}")
    private int num;
}