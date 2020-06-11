package io.github.donggi.bean;

public class Hello24 {
    public static class C1 {        
        public void init() {
            System.out.println("Hello24$C1 Bean Created...");
        }
    }
    public static class C2 {        
        public void close() {
            System.out.println("Hello24$C2 Bean Destroyed...");
        }
    }
}
