import lombok.Value;

public class LombokValueExample {
    @Value
    static class Point {
        Integer x, y;
    }

    public static void main(String[] args) {
        Point p = new Point(1, 2);
        System.out.println(p); // LombokValueExample.Point(x=1, y=2)
        // p.x = 123; // The final field LombokValueExample.Point.x cannot be assigned
    }

}