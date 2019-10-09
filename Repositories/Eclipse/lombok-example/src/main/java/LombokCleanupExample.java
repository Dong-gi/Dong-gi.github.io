import lombok.Cleanup;

public class LombokCleanupExample {

    static class Resource1 {
        public void close() {
            System.out.println(this.getClass());
        }
    }

    static class Resource2 {
        public void clean() {
            System.out.println(this.getClass());
        }
    }

    public static void main(String[] args) {
        @Cleanup Resource1 r1 = new Resource1();
        {
            @Cleanup("clean") Resource2 r2 = new Resource2();
        }
    }
    /*
class LombokCleanupExample$Resource2
class LombokCleanupExample$Resource1
     */
}