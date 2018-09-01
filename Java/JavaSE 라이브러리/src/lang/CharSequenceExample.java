package lang;

import java.util.Arrays;

public class CharSequenceExample {

    public static void main(String[] args) {
        var str = "안녕 🀂";
        System.out.println(str);
        System.out.println(str.length());
        System.out.println(Arrays.toString(str.chars().toArray()));
        System.out.println(Arrays.toString(str.codePoints().toArray()));
        System.out.println(Character.toCodePoint(str.charAt(3), str.charAt(4)));
    }

}
