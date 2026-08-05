package net;

import java.io.IOException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class InetAddressTest {

    @Test
    void getAllByNameTest() throws IOException {
        for(var addr : InetAddress.getAllByName("www.naver.com")) {
            System.out.printf("%s : %s\n", addr, addr.isReachable(1000));
            // www.naver.com/125.209.222.142 : false
            // www.naver.com/125.209.222.141 : false
        }
    }

}
