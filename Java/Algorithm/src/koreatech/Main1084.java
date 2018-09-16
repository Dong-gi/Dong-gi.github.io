package koreatech;

import java.util.*;
import java.util.stream.Collectors;

public class Main1084 {

    static class Section {
        int start;
        int end;

        Section(int start, int end) {
            this.start = start;
            this.end = end;
        }

        boolean merge(Section another) {
            // System.out.printf("this : %s, another : %s\n", this.toString(), another.toString());
            if(start > another.end || end < another.start) {
                return false;
            }
            start = start < another.start? start : another.start;
            end = end > another.end? end : another.end;
            return true;
        }

        @Override
        public String toString() {
            return start + " " + end;
        }
    }

    public static void main(String[] args) {
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = in.nextInt();

        for(int testCase = 0; testCase < numOfTestCase; ++testCase) {


            final ArrayList<Section> sections = new ArrayList<>();
            final int numOfInput = in.nextInt();

            for(int i = 0; i < numOfInput; ++i) {
                Section s = new Section(in.nextInt(), in.nextInt());
                if(!sections.isEmpty()) {
                    for(int j = 0; j < sections.size(); ++j) {
                        if(sections.get(j).merge(s)) {
                            s = sections.remove(j);
                            j = -1;
                        }
                    }
                }
                sections.add(s);
            }

            System.out.println(sections.size());
            System.out.println(sections
                    .stream()
                    .sorted((s1, s2) -> s1.start - s2.start)
                    .map(section -> section.toString())
                    .collect(Collectors.joining(" ")));
        }

    }
}
