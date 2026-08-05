package net;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.collections4.MapUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class HttpURLConnectionTest {
    static URL url;
    static HttpURLConnection conn;

    @BeforeAll
    static void beforeAll() throws Exception {
        url = new URL("https://dong-gi.github.io/posts/tip/regex.html");
        conn = (HttpURLConnection) url.openConnection();
    }

    @Test
    void getResponseMessageTest() throws IOException {
        System.out.println(conn.getHeaderField(null));  // HTTP/1.1 200 OK
        System.out.println(conn.getResponseCode());     // 200
        System.out.println(conn.getResponseMessage());  // OK
    }

    @Test
    void getHeaderFieldsTest() {
        MapUtils.verbosePrint(System.out, null, conn.getHeaderFields());
        /*
        {
            null = [HTTP/1.1 200 OK]
            Server = [GitHub.com]
            Access-Control-Allow-Origin = [*]
            Connection = [keep-alive]
            Last-Modified = [Tue, 03 Sep 2019 23:53:52 GMT]
            Date = [Fri, 06 Sep 2019 00:01:05 GMT]
            Cache-Control = [max-age=600]
            Expires = [Fri, 06 Sep 2019 00:07:00 GMT]
            Content-Length = [308]
            Content-Type = [text/html; charset=utf-8]
            ...
        }
         */
    }

    @Test
    void getInputStreamTest() throws Exception {
        System.out.println(new String(conn.getInputStream().readAllBytes()));
        /*
         <hr>

        <details>
            <summary>일본어 검색</summary>
            <ol>
                <li>히라가나 : \u3041-\u3096</li>
                <li>가타카나 : \u30A1-\u30FA</li>
                <li>한자 : \u4E00-\u9FEF, \u3400-\u4DBF, \uF900-\uFADF</li>
                <li>특문 : \u3000-\u303F, \uFF00-\uFF9F</li>
            </ol>
        </details>

        <hr>
         */
    }

}
