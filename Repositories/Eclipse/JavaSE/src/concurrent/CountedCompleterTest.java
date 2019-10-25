package concurrent;

import java.util.Arrays;
import java.util.concurrent.CountedCompleter;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

// Sample code from "https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/util/concurrent/CountedCompleter.html"
class CountedCompleterTest {

    static <T> void forEach1(Consumer<T> action, T... arr) {
        class Task extends CountedCompleter<Void> {
            final int low, high;
            Task(Task parent, int low, int high) {
                super(parent); this.low = low; this.high = high;
            }
            @Override
            public void compute() {
                if(high - low >= 2) {
                    int mid = (low + high) >>> 1;
                    setPendingCount(2);
                    new Task(this, low, mid).fork();
                    new Task(this, mid, high).fork();
                } else
                    action.accept(arr[low]);
                tryComplete();
            }
        }
        new Task(null, 0, arr.length).invoke();
        System.out.println(">> Complted");
    }

    static <T> void forEach2(Consumer<T> action, T... arr) {
        class Task extends CountedCompleter<Void> {
            final int low, high;
            Task(Task parent, int low, int high) {
                super(parent); this.low = low; this.high = high;
            }
            @Override
            public void compute() {
                for(var size = high - low; size >= 2; size /= 2) {
                    addToPendingCount(1);
                    new Task(this, low + size/2, low + size).fork();
                }
                action.accept(arr[low]);
                propagateCompletion();
            }
        }
        new Task(null, 0, arr.length).invoke();
        System.out.println(">> Complted");
    }

    static <T> void forEach3(Consumer<T> action, T... arr) {
        class Task extends CountedCompleter<Void> {
            final int low, high;
            Task(Task parent, int low, int high) {
                super(parent, 31 - Integer.numberOfLeadingZeros(high - low));
                this.low = low; this.high = high;
            }
            @Override
            public void compute() {
                for(var size = high - low; size >= 2; size /= 2)
                    new Task(this, low + size/2, low + size).fork();
                action.accept(arr[low]);
                propagateCompletion();
            }
        }
        new Task(null, 0, arr.length).invoke();
        System.out.println(">> Complted");
    }
    
    @Test
    void forEachTest() {
        forEach1((i) -> System.out.printf("%d ", i), 1, 10, 2, 9, 3, 8, 4, 7, 5, 6);
        forEach2((i) -> System.out.printf("%d ", i), 1, 10, 2, 9, 3, 8, 4, 7, 5, 6);
        forEach3((i) -> System.out.printf("%d ", i), 1, 10, 2, 9, 3, 8, 4, 7, 5, 6);
    }
/*
6 5 7 8 10 1 2 4 3 9 >> Complted
1 7 5 4 8 2 6 10 3 9 >> Complted
1 10 2 9 3 5 7 8 4 6 >> Complted
 */
    
    static <T> boolean contains(final T item, T... arr) {
        final AtomicReference<T> ref = new AtomicReference<T>();
        class Searcher extends CountedCompleter<Boolean> {
            final int low, high;
            Searcher(Searcher parent, int low, int high) {
                super(parent); this.low = low; this.high = high;
            }
            @Override
            public Boolean getRawResult() {
                return ref.get() != null;
            }
            @Override
            public void compute() {
                int l = low, h = high;
                while(ref.get() == null && h >= l) {
                    if(h - l >= 2) {
                        var mid = (l + h) >>> 1;
                        addToPendingCount(1);
                        new Searcher(this, mid, high).fork();
                        h = mid;
                    } else {
                        if(arr[l].equals(item) && ref.compareAndSet(null, arr[l]))
                            quietlyCompleteRoot();
                        break;
                    }
                }
                tryComplete();
            }
            
        }
        var result = new Searcher(null, 0, arr.length).invoke();
        System.out.printf("%s in %s : %s\n", item, Arrays.deepToString(arr), result);
        return result;
    }
    
    @Test
    void containsTest() {
        contains(7, new Integer[] {1, 10, 2, 9, 3, 8, 4, 7, 5, 6});
        contains(0, new Integer[] {1, 10, 2, 9, 3, 8, 4, 7, 5, 6});
    }
/*
7 in [1, 10, 2, 9, 3, 8, 4, 7, 5, 6] : true
0 in [1, 10, 2, 9, 3, 8, 4, 7, 5, 6] : false
 */
    
    static void runAfter(final ForkJoinTask<?>[] tasks, ForkJoinTask<?> allAfter) {
        class RunAfter extends CountedCompleter<Void> {
            RunAfter() {
                super(null, tasks.length - 1);
            }
            @Override
            public void compute() { throw new RuntimeException("Don't call it"); } // never called
            @Override
            public void onCompletion(CountedCompleter<?> caller) {
                allAfter.invoke();
            }
        }
        var after = new RunAfter();
        for(var task : tasks)
            new CountedCompleter<Void>(after) {
                @Override
                public void compute() {
                    task.invoke();
                    tryComplete();
                }
            }.fork();
        after.join();
    }
    
    @Test
    void runAfterTest() {
        var tasks = new ForkJoinTask<?>[77];
        for(var i = 0; i < tasks.length; ++i) {
            final var millis = i;
            tasks[i] = ForkJoinTask.adapt(() -> {
                try {
                    Thread.sleep(millis);
                } catch (InterruptedException e) {}
            });
        }
        runAfter(tasks, ForkJoinTask.adapt(() -> System.out.println("All pre-requisite tasks completed")));
    }
}