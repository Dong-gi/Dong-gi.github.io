package concurrent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.RecursiveTask;

import org.junit.jupiter.api.Test;

// Sample code from "https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/util/concurrent/RecursiveTask.html"
class RecursiveTaskTest {

    @Test
    void test() {
        // 0, 1, 2, 3, 4, 5, 6,  7,  8
        // 0, 1, 1, 2, 3, 5, 8, 13, 21
        System.out.println(new FibonacciTask(8).invoke()); // 21
    }

}

class FibonacciTask extends RecursiveTask<Integer> {
    final int n;
    FibonacciTask(int n) {
        this.n = n;
    }
    @Override
    protected Integer compute() {
        if(n < 2) return n;
        var f1 = new FibonacciTask(n-1).fork();
        var f2 = new FibonacciTask(n-2).fork();
        return f2.join() + f1.join();
    }
}