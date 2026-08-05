package concurrent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

import org.junit.jupiter.api.Test;

// Sample Code from "https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/util/concurrent/RecursiveAction.html"
class RecursiveActionTest {

    @Test
    void test() {
        final var pool = ForkJoinPool.commonPool();
        var arr = new Integer[] {-69, 87,-85,-82,-25, 64, 98,-72,-92, 26, 55, 86, -6, 85,-91, 41,-76, 49, -4,-51, 92, 72,-95,-84, 99, 80,-14, 17, 96,-46};
        var task = new MergeSortAction<Integer>(arr);
        task.invoke();
        System.out.println(Arrays.deepToString(arr));
// [-95, -92, -91, -85, -84, -82, -76, -72, -69, -51, -46, -25, -14, -6, -4, 17, 26, 41, 49, 55, 64, 72, 80, 85, 86, 87, 92, 96, 98, 99]
    }

}

class MergeSortAction<T extends Comparable<T>> extends RecursiveAction {
    private final T[] arr;
    final int low, high; // low(inclusive index), high(exclusive index)

    MergeSortAction(T... arr) {
        this(0, arr.length, arr);
    }

    MergeSortAction(int low, int high, T... arr) {
        this.arr = arr;
        this.low = low;
        this.high = high;
    }

    @Override
    protected void compute() {
        if(high - low < 5) {
            Arrays.parallelSort(arr, low, high);
        } else {
            var mid = (low + high) >>> 1;
            invokeAll(new MergeSortAction<T>(low, mid, arr), new MergeSortAction<T>(mid, high, arr));
            merge(mid);
        }
    }
    private void merge(int mid) {
        T[] tmp = Arrays.copyOfRange(arr, low, mid);
        for(int i = 0, j = low, k = mid; i < tmp.length; ++j) {
            arr[j] = (k == high || tmp[i].compareTo(arr[k]) < 0)? tmp[i++] : arr[k++];
        }
    }
}