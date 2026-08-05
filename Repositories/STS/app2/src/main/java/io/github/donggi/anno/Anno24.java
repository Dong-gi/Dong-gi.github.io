package io.github.donggi.anno;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;

@Configuration
@ComponentScan(
        basePackages = "io.github.donggi.scan",
        includeFilters = @Filter(type = FilterType.ASPECTJ, pattern = "io.github.donggi.scan.*Service"),
        excludeFilters = @Filter(type = FilterType.REGEX, pattern = ".+\\.[AC]Service"))
public class Anno24 {}