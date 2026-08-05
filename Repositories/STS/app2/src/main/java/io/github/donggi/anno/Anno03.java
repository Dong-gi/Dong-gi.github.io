package io.github.donggi.anno;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Data;

@Configuration
class Anno03Config {
    @Bean
    public String anno03Message() {
        return "Default message for Anno03";
    }
}

@Data
@Component
public class Anno03 {

    private String anno03Message;

    @Autowired
    public Anno03(String anno03Message) {
        this.anno03Message = anno03Message;
    }
    public Anno03(long time) {
        this.anno03Message = new Date(time).toString();
    }

}