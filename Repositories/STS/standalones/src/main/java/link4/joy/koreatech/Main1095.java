package link4.joy.koreatech;

import java.util.*;

public class Main1095 {
    static class NaturalName implements Comparable<NaturalName> {
        private static volatile int serial = 0;
        String originalName;
        NamePart[] parts;
        int _serial = serial++;

        static class NamePart {
            boolean isNum = false;
            String part;

            NamePart(boolean isNum, String part) {
                this.isNum = isNum;
                this.part = part;
            }
        }

        NaturalName(String name) {
            originalName = new String(name);
            String[] chars = Arrays.stream(name.split("[\\d]+")).filter(str -> !str.isEmpty()).toArray(String[]::new);
            String[] nums = Arrays.stream(name.split("[\\D]+")).filter(str -> !str.isEmpty()).toArray(String[]::new);
            parts = new NamePart[chars.length + nums.length];
            for (int i = 0, j = 0; i + j < parts.length;) {
                try {
                    if (name.startsWith(chars[i])) {
                        name = name.replaceFirst(chars[i], "");
                        parts[i + j] = new NamePart(false, chars[i++]);
                    }
                } catch (Exception e) {
                }
                try {
                    if (name.startsWith(nums[j])) {
                        name = name.replaceFirst(nums[j], "");
                        parts[i + j] = new NamePart(true, nums[j++]);
                    }
                } catch (Exception e) {
                }
            }
        }

        @Override
        public String toString() {
            return originalName;
        }

        @Override
        public int compareTo(NaturalName another) {
            try {
                for (int i = 0;; ++i) {
                    if (parts[i].isNum && another.parts[i].isNum) {
                        Long mine = Long.valueOf(parts[i].part);
                        Long yours = Long.valueOf(another.parts[i].part);
                        if (mine.equals(yours))
                            continue;
                        return mine.compareTo(yours);
                    }
                    if (parts[i].isNum)
                        return -1;
                    if (another.parts[i].isNum)
                        return 1;
                    if (parts[i].part.equals(another.parts[i].part))
                        continue;
                    return parts[i].part.compareTo(another.parts[i].part);
                }
            } catch (Exception e) {
            }

            if (parts.length == another.parts.length)
                return _serial - another._serial;
            return parts.length - another.parts.length;
        }
    }

    public static void main(String[] args) {
        @SuppressWarnings("resource")
        final Scanner in = new Scanner(System.in);
        final int numOfTestCase = Integer.parseInt(in.nextLine().trim());
        NaturalName[] names = new NaturalName[numOfTestCase];

        for (int testCase = 0; testCase < numOfTestCase; ++testCase) {
            names[testCase] = new NaturalName(in.nextLine().trim());
        }

        Arrays.sort(names);
        for (NaturalName name : names) {
            System.out.println(name);
        }
    }
}