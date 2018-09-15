package koreatech;

import java.util.*;

public class Main1048 {

    static class AP {
        int idx;
        float numOfServe;

        AP(int idx, float nextNumOfServe) {
            this.idx = idx;
            this.numOfServe = nextNumOfServe;
        }        
    }
    
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int testCase = in.nextInt();
        while(testCase-- > 0) {
            int numOfDorms = in.nextInt();
            int numOfAPs = in.nextInt();
            int[] numOfPple = new int[numOfDorms];
            int sumOfPple = 0;
            for(int i = 0; i < numOfDorms; ++i)
                sumOfPple += numOfPple[i] = in.nextInt();

            if(numOfDorms == 1) {
                System.out.println((int)Math.ceil((float)sumOfPple/numOfAPs));
                continue;
            }

            int[] numOfAPinDorms = new int[numOfDorms];
            PriorityQueue<AP> aps = new PriorityQueue<>((ap1, ap2) -> Float.compare(ap2.numOfServe, ap1.numOfServe));
            
            // 일단 하나씩 분배
            Arrays.fill(numOfAPinDorms, 1);
            for(int i = 0; i < numOfDorms; ++i)
                aps.add(new AP(i, numOfPple[i]));
            numOfAPs -= numOfDorms;

            /*for(AP ap : aps) {
                System.out.println(ap.idx + ":" + ap.numOfServe);
            }*/

            while(numOfAPs-- > 0) {
                AP ap = aps.poll();
                AP ap2 = aps.peek();
                do {
                    //System.out.println("selected : " + ap.idx + ":" + ap.numOfServe);
                    numOfAPinDorms[ap.idx] += 1;
                    ap.numOfServe = (float)numOfPple[ap.idx]/(numOfAPinDorms[ap.idx]);
                } while(ap.numOfServe > ap2.numOfServe && numOfAPs-- > 0);
                aps.add(ap);
            }

            /*for(AP ap : aps) {
                System.out.println(ap.idx + ":" + ap.numOfServe);
            }*/

            System.out.println((int)Math.ceil(aps.peek().numOfServe));
        }
    }
}