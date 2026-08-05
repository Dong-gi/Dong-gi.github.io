package text;

import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.junit.jupiter.api.Test;



class SimpleDateFormatTest { // 15 고정

    @Test
    void eraTest() throws ParseException {
        var formatter = new SimpleDateFormat("GG");
        var source = "BC";
        System.out.println(formatter.parse(source)); // Sun Jan 01 00:00:00 KST 1970
        assertTrue(source.contentEquals(formatter.format(formatter.parse(source))));
    }

    @Test
    void normalTest() throws ParseException {
        var formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        var source = "2019-10-07 16:19:13";
        System.out.println(formatter.parse(source)); // Mon Oct 07 16:19:13 KST 2019
        assertTrue(source.contentEquals(formatter.format(formatter.parse(source))));
    }

    @Test
    void weekYearTest() throws ParseException {
        var formatter = new SimpleDateFormat("YYYY-'W'ww-u(EEE)");
        var calendar = GregorianCalendar.getInstance();
        calendar.set(2019, Calendar.DECEMBER, 31);
        var date = calendar.getTime();

        if(!GregorianCalendar.class.isAssignableFrom(formatter.getCalendar().getClass()))
            formatter.setCalendar(GregorianCalendar.getInstance());
        System.out.println(formatter.format(date)); // 2020-W01-2(화)
    }

    @Test
    void tzTest() throws ParseException {
        var formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss.SSSZ(aa)");
        var date = new Date(System.currentTimeMillis());
        System.out.println(formatter.format(date)); // 2019-10-07 04:55:58.237+0900(오후)
        System.out.println(formatter.parse(formatter.format(date))); // Mon Oct 07 16:55:58 KST 2019
    }

}
