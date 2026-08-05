package io.github.donggi.anno;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.stereotype.Component;

import lombok.Data;

@Configuration
@PropertySource(value = "src/main/resource/anno19.properties", encoding = "UTF-8", ignoreResourceNotFound = true)
class Anno19Config {
    @Bean
    public static ConversionService conversionService() {
        DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();
        conversionService.addConverter(new Converter<String, Anno19Data>() {
            @Override
            public Anno19Data convert(String source) {
                Anno19Data data = new Anno19Data();
                String[] arr = source.split(" ");
                data.setX(Integer.parseInt(arr[0]));
                data.setY(Integer.parseInt(arr[1]));
                data.setZ(Integer.parseInt(arr[2]));
                return data;
            }
        });
        return conversionService;
    } 
}

@Data
class Anno19Data {
    private int x;
    private int y;
    private int z;
}

@Data
@Component
public class Anno19 {
    @Value("${anno19.data}")
    private Anno19Data data;
}