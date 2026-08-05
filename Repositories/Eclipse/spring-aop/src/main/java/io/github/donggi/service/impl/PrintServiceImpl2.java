package io.github.donggi.service.impl;

import org.apache.commons.lang3.exception.ExceptionUtils;

import io.github.donggi.service.PrintService;

public class PrintServiceImpl2 implements PrintService {

    @Override
    public String print(String message) {
        ExceptionUtils.rethrow(new RuntimeException("그냥 던져봤어"));
        return null;
    }

}
