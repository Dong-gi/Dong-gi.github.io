package nio.sctp;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import com.sun.nio.sctp.SctpServerChannel;

class SctpChannelTest {

    static final int BUF_SIZE = 1024;
    static final int PORT = 35003;

    @Test
    void test() throws IOException {
        try (
                var serverChannel = SctpServerChannel.open().bind(new InetSocketAddress(PORT));) {
            System.out.println(serverChannel);
/*
java.lang.UnsupportedOperationException: SCTP not supported on this platform
 */
        }
    }

}
