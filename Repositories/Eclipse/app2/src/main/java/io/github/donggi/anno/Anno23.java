package io.github.donggi.anno;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import io.github.donggi.scan1.ForScan1;
import io.github.donggi.scan1.NotScan1;

import org.springframework.context.annotation.ComponentScan.Filter;

@Configuration
@ComponentScan(basePackages = "io.github.donggi.scan1",
includeFilters = @Filter(classes = ForScan1.class),
excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = NotScan1.class))
public class Anno23 {}