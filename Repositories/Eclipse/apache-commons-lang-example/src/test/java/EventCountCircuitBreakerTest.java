import static org.junit.Assert.*;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.EventCountCircuitBreaker;
import org.junit.Test;

public class EventCountCircuitBreakerTest {

    public void arg3ConstructorTest() throws Exception {
        var breaker = new EventCountCircuitBreaker(5, 1, TimeUnit.SECONDS);
        System.out.println(new Date());
        for(var i = 0; i < 25; ) {
            while(!breaker.checkState())
                Thread.sleep(100);
            if(!breaker.incrementAndCheckState())
                System.out.append('\n');
            else
                System.out.append(' ').print(i++);
        }
        System.out.append('\n').println(new Date());
        /*
Mon Sep 16 09:48:32 KST 2019
 0 1 2 3 4
 5 6 7 8 9
 10 11 12 13 14
 15 16 17 18 19
 20 21 22 23 24
Mon Sep 16 09:48:37 KST 2019
         */
    }
    
    public void arg4ConstructorTest() throws Exception {
        var breaker = new EventCountCircuitBreaker(5, 1, TimeUnit.SECONDS, 2);
        for(var i = 0; i < 13; ) {
            Thread.sleep(160);
            System.out.printf("%4d | ", System.currentTimeMillis() % 10000);
            if(!breaker.checkState())
                continue;
            else {
                System.out.printf("task %d\n", i++);
                breaker.incrementAndCheckState();
            }
        }
        /*
9633 | task 0
9793 | task 1
9953 | task 2
 113 | task 3
 273 | task 4
 433 | task 5
 593 |  753 |  914 | 1074 | 1234 | 1394 | 1554 | task 6 // 1394 시점에서는 초당 요청이 2 미만이지만 CLOSE되지 않았음을 확인할 수 있다
1714 | task 7
1874 | task 8
2035 | task 9
2195 | task 10
2355 | task 11
2515 | 2675 | 2835 | 2995 | 3156 | 3316 | 3477 | task 12 // 마찬가지로 3316 시점에서도 CLOSE되지 않았다
         */
    }
    
    public void arg6ConstructorTest() throws Exception {
        var breaker = new EventCountCircuitBreaker(5, 1, TimeUnit.SECONDS, 5, 2, TimeUnit.SECONDS);
        for(var i = 0; i < 13; ) {
            Thread.sleep(101);
            System.out.printf("%4d | ", System.currentTimeMillis() % 10000);
            if(!breaker.checkState())
                continue;
            else {
                System.out.printf("fail %d\n", i++);
                breaker.incrementAndCheckState();
            }
        }
        /*
1556 | fail 0
1661 | fail 1
1762 | fail 2
1864 | fail 3
1965 | fail 4
2067 | fail 5
2168 | 2270 | 2371 | 2473 | 2574 | 2676 | 2778 | 2879 | 2981 | 3082 | 3184 | 3285 | 3387 | 3488 | 3590 | 3691 | 3793 | 3894 | 3996 | 4097 | fail 6
4199 | fail 7
4300 | fail 8
4402 | fail 9
4503 | fail 10
4604 | fail 11
4706 | 4807 | 4908 | 5009 | 5110 | 5212 | 5313 | 5415 | 5516 | 5618 | 5719 | 5821 | 5922 | 6024 | 6125 | 6227 | 6328 | 6430 | 6531 | 6633 | fail 12
         */
    }
}
