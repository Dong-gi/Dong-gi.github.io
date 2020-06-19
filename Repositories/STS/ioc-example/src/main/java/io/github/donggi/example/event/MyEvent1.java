package io.github.donggi.example.event;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

@Getter
public class MyEvent1 extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final String msg;

    public MyEvent1(Object source, String msg) {
        super(source);
        this.msg = msg;
    }

}
