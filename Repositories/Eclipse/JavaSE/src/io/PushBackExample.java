package io;

import java.io.PushbackReader;
import java.io.StringReader;

public class PushBackExample {

    public static void main(String[] args) throws Exception {
        var str = "This is original statement.";
        System.out.println(str);

        var reader = new PushbackReader(new StringReader(str), 100);
        var buf = new char[5];
        reader.read(buf);
        reader.unread("[Overwrite]".toCharArray());
        buf = new char[100];
        reader.read(buf);
        System.out.println(new String(buf).trim());

        reader = new PushbackReader(new StringReader("This is original statement."), 100);
        buf = new char[15];
        reader.read(buf);
        reader.unread("[Overwrite]".toCharArray());
        buf = new char[100];
        reader.read(buf);
        System.out.println(new String(buf).trim());
    }

}