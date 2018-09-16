package koreatech;

import java.util.*;

public class Main1082 {

    public static final String ext = ".mov";

    static class NaturalName {
        String name;
        int num;

        NaturalName(String name, int num) {
            this.name = name;
            this.num = num;
        }

        @Override
        public String toString() {
            return name + "_" + num + ext;
        }
    }

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = Integer.parseInt(in.nextLine());
        NaturalName[] names = new NaturalName[numOfTestCase];

        for(int testCase = 0; testCase < numOfTestCase; ++testCase) {
            args = in.nextLine().replaceAll(".mov", "").split("_");
            names[testCase] = new NaturalName(args[0], Integer.parseInt(args[1]));
        }
        Arrays.sort(names, (n1, n2) -> n1.num - n2.num);
        for(NaturalName n : names) {
            System.out.println(n);
        }
    }
}
