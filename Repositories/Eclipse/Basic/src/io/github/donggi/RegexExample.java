package io.github.donggi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;



class RegexExample { // 12 고정

    @Test
    void test() { // 15 고정
        var pattern = Pattern.compile("(\\w+)(\\s+(\\d+))");
        var matcher = pattern.matcher("Ryzen 3600");
        matcher.matches();
        for(var i = 0; i <= matcher.groupCount(); ++i)
            System.out.printf("Group %d : [%s]\n", i, matcher.group(i));
        /*
         Group 0 : [Ryzen 3600]
         Group 1 : [Ryzen]
         Group 2 : [ 3600]
         Group 3 : [3600]
         */
        
        pattern = Pattern.compile("(\\w+)(?:\\s+(\\d+))");
        matcher = pattern.matcher("Ryzen 3600");
        matcher.matches();
        for(var i = 0; i <= matcher.groupCount(); ++i)
            System.out.printf("Group %d : [%s]\n", i, matcher.group(i));
        /*
         Group 0 : [Ryzen 3600]
         Group 1 : [Ryzen]
         Group 2 : [3600]
         */
        
        assertTrue(Pattern.matches("hello", "hello"));
        assertTrue(Pattern.matches("\\0150\\0145\\0154\\0154\\0157", "hello"));
        assertTrue(Pattern.matches("\\x68\\x65\\x6C\\x6C\\x6F", "hello"));
        assertTrue(Pattern.matches("\\uC548\\uB155", "안녕"));
        assertTrue(Pattern.matches("\\N{HANGUL SYLLABLES C548}\\N{HANGUL SYLLABLES B155}", "안녕"));
        assertTrue(Pattern.matches("^[eloh]+$", "hello"));
        assertTrue(Pattern.matches("^[^a-dp-z]+$", "hello"));
        assertTrue(Pattern.matches("^[e-o]+$", "hello"));
        assertTrue(Pattern.matches("^[e-hl-o]+$", "hello"));
        assertTrue(Pattern.matches("^[a-z&&[e-o]]+$", "hello"));
        assertTrue(Pattern.matches("^[a-z&&[^a-dp-z]]+$", "hello"));
        
        pattern = Pattern.compile("(?ms).(?=←)");
        matcher = pattern.matcher("1234←5678←\n4321←8765←");
        System.out.println(matcher.replaceAll("[$0]"));
        /*
         123[4]←567[8]←
         432[1]←876[5]←
         */
        
        pattern = Pattern.compile("(?<DupWord>\\w+)\\k<DupWord>");
        var msg = "appleapple, banana";
        matcher = pattern.matcher(msg);
        while(matcher.find()) {
            System.out.print(msg);
            msg = matcher.replaceAll("$1");
            System.out.append(" → ").println(msg);
            matcher = pattern.matcher(msg);
        }
        /*
         appleapple, banana → apple, bana
         apple, bana → aple, bana
         */
        
        pattern = Pattern.compile("(?<DupWord>\\w+)\\1");
        msg = "appleapple, banana";
        matcher = pattern.matcher(msg);
        while(matcher.find()) {
            System.out.print(msg);
            msg = matcher.replaceAll("$1");
            System.out.append(" → ").println(msg);
            matcher = pattern.matcher(msg);
        }
        /*
         appleapple, banana → apple, bana
         apple, bana → aple, bana
         */

        var badWords = new String[] { ".지" };
        var messages = new String[] { "공지사항이 있습니다 : 공지를 공지라 부르지 못하고...", "이건 건전한 문장입니다." };
        for (var message : messages) {
            for (var badWord : badWords) {
                // ^(.(?!badWord))*$ // 금칙어가 붙지 않는 문자가 처음부터 끝까지 이어진다.
                if(Pattern.matches(String.format("^(.(?!%s))*$", badWord), message))
                    continue;
                pattern = Pattern.compile(badWord);
                message = pattern.matcher(message).replaceAll("♥♥");
            }
            System.out.println(message);
        }
        /*
         ♥♥사항이 있습니다 : ♥♥를 ♥♥라 부♥♥ 못하고...
         이건 건전한 문장입니다.
         */
        
        "안녕".codePoints().forEach(x -> System.out.append(' ').print(Integer.toHexString(x)));
    }

}
