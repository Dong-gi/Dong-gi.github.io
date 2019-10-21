package concurrent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;

class CompletionStageTest {
    
    static <T> T asIs(T t) {
        System.out.printf("%d : %s\n", System.currentTimeMillis(), t);
        return t;
    }
    
    static <T> T asIsEx(T t) {
        System.out.printf("%d(Exception) : %s\n", System.currentTimeMillis(), t);
        rethrow(new RuntimeException("그냥 던짐"));
        return t;
    }
    
    static <T extends Throwable> void rethrow(T t) {
        throw new RuntimeException(t);
    }

    @Test
    void thenComposeTest() throws InterruptedException, ExecutionException {
        var cf = CompletableFuture.supplyAsync(() -> asIs(1));
        var cf2 = cf.thenCompose(x -> CompletableFuture.supplyAsync(() -> asIs(10)));
        assertTrue(cf2.get() == 10);
/*
1571620621740 : 1
1571620621741 : 10
 */
    }
    
    @Test
    void exceptionallyComposeTest() throws InterruptedException, ExecutionException {
        var cf = CompletableFuture.supplyAsync(() -> asIsEx(1));
        var cf2 = cf.exceptionallyCompose((t) -> CompletableFuture.supplyAsync(() -> asIs(10)));
        assertTrue(cf2.get() == 10);
    }
/*
1571620923006(Exception) : 1
1571620923009 : 10
 */
}
