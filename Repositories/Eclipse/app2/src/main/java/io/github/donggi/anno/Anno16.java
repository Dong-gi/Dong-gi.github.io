package io.github.donggi.anno;

import javax.annotation.Resource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
class Anno16Data<T> {
    private String message;

    public Anno16Data(String message) {
        this.message = String.format("%s#%s", this.getClass().getSimpleName(), message);
    }
}

@Configuration
class Anno16Config {
    @Bean(name = "anno16-data1")
    public static Anno16Data<String> anno16data1() {
        return new Anno16Data<String>("String");
    }
    @Bean
    public static Anno16Data<Integer> anno16data2() {
        return new Anno16Data<Integer>("Integer");
    }
}

@Getter
@Setter
@Component
public class Anno16 {
    @Resource(name = "anno16-data1")
    private Anno16Data<Integer> data;
}