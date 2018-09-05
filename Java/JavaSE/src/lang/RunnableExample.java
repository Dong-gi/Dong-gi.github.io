package lang;


public class RunnableExample {

    public static void main(String[] args) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                System.out.println("Runnable 1");
            }
        }).start();
        new Thread(() -> System.out.println("Runnable 2")).start();
    }

}
