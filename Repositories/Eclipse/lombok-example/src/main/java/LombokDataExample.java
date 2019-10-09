import lombok.Data;

public class LombokDataExample {
    @Data
    static class Point {
        Integer x, y;
    }

    public static void main(String[] args) {
        Point p = new Point();
        System.out.println(p); // LombokDataExample.Point(x=null, y=null)
        p.setX(1);
        p.setY(2);
        System.out.println(p); // LombokDataExample.Point(x=1, y=2)
    }

}