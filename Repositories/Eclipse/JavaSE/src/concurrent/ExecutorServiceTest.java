package concurrent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import lombok.AllArgsConstructor;

@AllArgsConstructor
class SimpleTask implements Callable<Integer> {
    final int seconds;

    @Override
    public Integer call() {
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {}
        return seconds;
    }
}

class ExecutorServiceTest {

    @Test()
    void invokeAllTest() throws InterruptedException, ExecutionException, TimeoutException {
        var es = Executors.newSingleThreadExecutor();
        var result = es.invokeAll(Arrays.asList(new SimpleTask(0), new SimpleTask(1), new SimpleTask(2)), 5, TimeUnit.SECONDS);
        for(var x : result)
            System.out.printf("isDone : %s, result : %s, isCancelled : %s\n", x.isDone(), x.isCancelled()? "" : x.get(), x.isCancelled());
/*
isDone : true, result : 0, isCancelled : false
isDone : true, result : 1, isCancelled : false
isDone : true, result : 2, isCancelled : false
 */
        result = es.invokeAll(Arrays.asList(new SimpleTask(1), new SimpleTask(3), new SimpleTask(5)), 0, TimeUnit.SECONDS);
        for(var x : result)
            System.out.printf("isDone : %s, result : %s, isCancelled : %s\n", x.isDone(), x.isCancelled()? "" : x.get(), x.isCancelled());
/*
isDone : true, result : , isCancelled : true
isDone : true, result : , isCancelled : true
isDone : true, result : , isCancelled : true
 */
    }

    @Test()
    void invokeAnyTest() throws InterruptedException, ExecutionException, TimeoutException {
        var es = Executors.newSingleThreadExecutor();
        var result = es.invokeAny(Arrays.asList(new SimpleTask(1), new SimpleTask(3), new SimpleTask(5)), 2, TimeUnit.SECONDS);
        assertTrue(result == 1);
        assertThrows(TimeoutException.class, () -> es.invokeAny(Arrays.asList(new SimpleTask(1), new SimpleTask(3), new SimpleTask(5)), 0, TimeUnit.SECONDS));
    }

}
