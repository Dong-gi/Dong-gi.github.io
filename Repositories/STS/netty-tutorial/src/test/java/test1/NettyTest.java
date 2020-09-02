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
        var ch = new EmbeddedChannel(new DiscardServerHandler());
        ch.config().setConnectTimeoutMillis(100);
        ch.writeInbound(Unpooled.wrappedBuffer("안녕 세상!".getBytes()));
        assertTrue(ch.<Object>readOutbound() == null);
    }

    @Test
    public void serverTest() throws Exception {
        var port = 55555;
        var server = new DiscardServer(port);
        server.start();

        var b = new Bootstrap();
        b.group(new NioEventLoopGroup()).channel(NioSocketChannel.class).handler(new DiscardServerHandler())
                .option(ChannelOption.AUTO_READ, true);
        var client = b.connect(new InetSocketAddress(port)).channel();
        client.writeAndFlush(Unpooled.wrappedBuffer("안녕 세상!".getBytes())).sync();
        Thread.sleep(100);
        server.close();
    }
}
