package io.github.donggi.anno;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Data;

@Configuration
class Anno04Config {
    @Bean
    public Long anno04Time() {
        return System.currentTimeMillis();
    }
    @Bean
    public String anno04Message() {
        return "Default message for Anno04";
    }
}

@Component
@Data
public class Anno04 {

    private String anno04Message;

    @Autowired
    public Anno04(String anno04Message, Long anno04Time) {
        this.anno04Message = anno04Message + "@" + new Date(anno04Time).toString();
    }

}