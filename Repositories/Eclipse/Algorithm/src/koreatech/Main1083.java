package koreatech;

import java.util.*;

public class Main1083 {

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = in.nextInt();

        for(int testCase = 0; testCase < numOfTestCase; ++testCase) {
            final BitSet start = new BitSet(25);
            final BitSet end = new BitSet(25);
            final int numOfInput = in.nextInt();

            for(int i = 0; i < numOfInput; ++i) {
                final int s = in.nextInt();
                final int e = in.nextInt();
                start.set(s, true);
                for(int j = s+1; j < e; ++j) {
                    start.set(j, true);
                    end.set(j, true);
                }
                end.set(e, true);
            }

            final BitSet xorTime = (BitSet) start.clone();
            xorTime.xor(end);
            int numOfSection = 0;
            final StringBuilder resultString = new StringBuilder();

            for(int i = 0; i <= 24;) {
                i = xorTime.nextSetBit(i);
                if(i < 0) break;
                resultString.append(i++).append(' ');
                i = xorTime.nextSetBit(i);
                resultString.append(i++).append(' ');
                numOfSection += 1;
            }

            System.out.println(numOfSection);
            System.out.println(resultString.substring(0, resultString.length()-1));
        }

    }
}
