package text;

import java.text.ChoiceFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.ParsePosition;

import org.junit.jupiter.api.Test;

class MessageFormatTest {

    // Example Code from https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/text/MessageFormat.html
    @Test
    void test1() throws ParseException {
        var formatter = new MessageFormat("The disk \"{1}\" contains {0}.");
        double[] filelimits = {0,1,2};
        String[] filepart = {"no files","one file","{0,number} files"};
        var fileform = new ChoiceFormat(filelimits, filepart);
        formatter.setFormatByArgumentIndex(0, fileform);

        String diskName = "MyDisk";
        for(var fileCount = 0; fileCount < 3; ++fileCount) {
            System.out.println(formatter.format(new Object[] { fileCount, diskName }));
        }
        /*
The disk "MyDisk" contains no files.
The disk "MyDisk" contains one file.
The disk "MyDisk" contains 2 files.
         */
    }

    // Example Code from https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/text/MessageFormat.html
    @Test
    void test2() throws ParseException {
        var formatter = new MessageFormat("There {0,choice,0#are no files|1#is one file|1<are {0,number,integer} files}.");
        for(var fileCount = 0; fileCount < 3; ++fileCount) {
            System.out.println(formatter.format(new Object[] { fileCount }));
        }
        /*
There are no files.
There is one file.
There are 2 files.
         */
    }

    // Example Code from https://docs.oracle.com/en/java/javase/12/docs/api/java.base/java/text/MessageFormat.html
    @Test
    void test3() throws ParseException {
        var formatter = new MessageFormat("{0,number,#.##}, {0,number,#.#}");
        System.out.println(formatter.parse(formatter.format( new Object[] { 3.141592 } ), new ParsePosition(0))[0]); // 3.1
    }
}
