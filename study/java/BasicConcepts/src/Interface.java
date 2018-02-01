interface Flyable {
    class Wing {}
    
    Wing LEFT_WING = new Wing();
    Wing RIGHT_WING = new Wing();
    
    void fly();
    default void flyFast() {
        System.out.println("빠르게 날기");
    }
}

public class Interface implements Flyable {

    public static void main(String[] args) {
        Wing w = new Flyable.Wing();
        new Interface().fly();
        new Interface().flyFast();
    }

    @Override
    public void fly() {
        System.out.println("날기 싫어");
    }

}