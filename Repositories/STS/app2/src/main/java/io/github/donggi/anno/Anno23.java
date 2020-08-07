package io.github.donggi.anno;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;

import io.github.donggi.scan.ForScan1;
import io.github.donggi.scan.NotScan1;

@Configuration
@ComponentScan(
        basePackages = "io.github.donggi.scan",
        includeFilters = @Filter(classes = ForScan1.class),
        excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = NotScan1.class))
public class Anno23 {}