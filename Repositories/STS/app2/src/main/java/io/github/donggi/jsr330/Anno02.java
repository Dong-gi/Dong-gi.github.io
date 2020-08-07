package io.github.donggi.jsr330;

import javax.inject.Inject;
import javax.inject.Named;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Configuration
class Anno02Config {
    @Bean(name = "jsr330-anno02-data1")
    public static Anno02Data anno02Data1() {
        var data = new Anno02Data();
        data.setMessage("Data 1");
        return data;
    }
    @Bean(name = "jsr330-anno02-data2")
    public static Anno02Data anno02Data2() {
        var data = new Anno02Data();
        data.setMessage("Data 2");
        return data;
    }
    @Bean
    public static Anno02 anno02() {
        return new Anno02();
    }
}

@Data
class Anno02Data {
    private String message;
}

@Data
public class Anno02 {
    @Inject
    @Named("jsr330-anno02-data2")
    private Anno02Data data;
}