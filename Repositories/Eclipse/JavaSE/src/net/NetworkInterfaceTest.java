package net;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class NetworkInterfaceTest {

    @Test
    void test() throws SocketException, UnknownHostException {
        for(var ni : NetworkInterface.networkInterfaces().toArray(NetworkInterface[]::new)) {
            System.out.println(ni);
            System.out.println(ni.inetAddresses().map(x -> x.toString()).collect(Collectors.joining(",")));
            /*
             name:lo (Software Loopback Interface 1)
             /127.0.0.1,/0:0:0:0:0:0:0:1
             ...
             name:eth1 (Realtek Gaming GBE Family Controller)
             /192.168.0.28,/fe80:0:0:0:cd21:2f4b:697b:c12c%eth1
             ...
             */
        }
    }

}
