package io.github.donggi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootApplication
public class LogbackGradleApplication {
    private static final Logger ERROR_LOG = LoggerFactory.getLogger("ERROR_LOG");

	public static void main(String[] args) {
		SpringApplication.run(LogbackGradleApplication.class, args);
		log.debug("Application really well started");
        ERROR_LOG.error("에러가 발생한 거시애오");
	}

}
