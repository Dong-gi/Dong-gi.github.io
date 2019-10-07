package text;

import java.text.NumberFormat;
import java.text.ParseException;

import org.junit.jupiter.api.Test;

class NumberFormatTest {

    @Test
    void test() throws ParseException {
        var formatter = NumberFormat.getNumberInstance();
        Number num = 1234.555;
        // java.lang.IllegalArgumentException: Cannot format given Object as a Number
        // System.out.println(formatter.format("1234.555"));
        System.out.println(formatter.format(num)); // 1,234.555
        System.out.println(formatter.parse(formatter.format(num))); // 1234.555
    }

}
