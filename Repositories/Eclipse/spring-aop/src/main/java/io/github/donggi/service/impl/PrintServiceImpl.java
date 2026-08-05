package io.github.donggi.service.impl;

import io.github.donggi.service.PrintService;

public class PrintServiceImpl implements PrintService {

    @Override
    public String print(String message) {
        try {
            Thread.sleep(100);
        } catch(Exception e) {}
        System.out.println(message);
        return message;
    }

}
