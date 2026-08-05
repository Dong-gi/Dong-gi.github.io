import static org.junit.Assert.*;

import org.apache.commons.lang3.BitField;
import org.junit.Test;

public class BitFieldTest {

    @Test
    public void test() {
        int color = 0;

        var r = new BitField(0b1111_1111_0000_0000_0000_0000);
        var g = new BitField(0b0000_0000_1111_1111_0000_0000);
        var b = new BitField(0b0000_0000_0000_0000_1111_1111);

        color = r.setValue(color, 12);
        color = g.setValue(color, 34);
        color = b.setValue(color, 56);

        System.out.println(color);
        System.out.println(r.getValue(color));
        System.out.println(g.getValue(color));
        System.out.println(b.getValue(color));
    }

}
