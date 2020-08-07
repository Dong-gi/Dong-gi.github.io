package io.github.donggi.bean;

import lombok.Data;

@Data
public class Hello20 {
    private long created;
    
    public Hello20() {
        created = System.currentTimeMillis();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public static class C1 extends Hello20 {}
    public static class C2 extends Hello20 {}
    public static class C3 extends Hello20 {}
}
