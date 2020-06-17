package io.github.donggi.anno;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.ComponentScan.Filter;

@Configuration
@ComponentScan(
        basePackages = "io.github.donggi.scan",
        includeFilters = @Filter(type = FilterType.CUSTOM, classes = io.github.donggi.scan.ScanFilter.class))
public class Anno25 {}