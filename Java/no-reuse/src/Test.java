import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

public class Test {
    
    private int num1;
    private int num2;
    
    public Test() { System.out.println(this); }
    public Test(int num1) { this.num1 = num1; }
    
    public Test(int num1, int num2) {
        // this(num1).num2 = num2; // Syntax error on token ".", ; expected at Test.<init>
        this(num1);
        this.num2 = num2;
    }
    
    @Override
    public String toString() {
        return num1 + "," + num2;
    }
    
    public static void main(String[] args) {
        System.out.println(new Test(1, 2));
        act1(Test::new);
        act2(Test::new);
    }
    
    @FunctionalInterface
    interface Act1 {
        Test act();
    }
    public static void act1(Act1 act1) {
        System.out.println(act1.act());
    }
    
    @FunctionalInterface
    interface Act2 {
        void act();
    }
    public static void act2(Act2 act2) {
        act2.act();
    }
}
