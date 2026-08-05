package test1;

import org.junit.Test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import static org.junit.Assert.*;

import java.net.InetSocketAddress;

public class NettyTest {
    @Test
    public void handlerTest() throws Exception {
        var ch = new EmbeddedChannel(new DiscardServerHandler("test"));
        ch.config().setConnectTimeoutMillis(100);
        ch.writeInbound(Unpooled.wrappedBuffer("안녕 세상!".getBytes()));
        assertTrue(ch.<Object>readOutbound() == null);
//        [main] INFO test1.DiscardServerHandler - Handler[test] received : 안녕 세상!
    }

    @Test
    public void serverTest() throws Exception {
        var port = 55555;
        var server = new DiscardServer(port);
        server.start();

        var b = new Bootstrap();
        b.group(new NioEventLoopGroup()).channel(NioSocketChannel.class).handler(new DiscardServerHandler("client"))
                .option(ChannelOption.AUTO_READ, true);
        var client = b.connect(new InetSocketAddress(port)).channel();
        client.writeAndFlush(Unpooled.wrappedBuffer("안녕 세상!".getBytes())).sync();
        Thread.sleep(100);
        server.close();
//        [main] INFO test1.DiscardServer - Server listening :55555
//        [nioEventLoopGroup-3-1] INFO test1.DiscardServerHandler - Handler[server] received : 안녕 세상!
    }
}
