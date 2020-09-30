package test2;

import org.junit.Test;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOption;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;

import static org.junit.Assert.*;

import java.net.InetSocketAddress;

public class NettyTest {
    @Test
    public void handlerTest() throws Exception {
        var ch = new EmbeddedChannel(new EchoServerHandler("test"));
        ch.config().setConnectTimeoutMillis(100);
        ch.writeInbound(Unpooled.wrappedBuffer("안녕 세상!".getBytes()));
        assertTrue(ch.<ByteBuf>readOutbound().toString(CharsetUtil.UTF_8).contentEquals("안녕 세상!"));
    }

    @Test
    public void serverTest() throws Exception {
        var port = 55555;
        var server = new EchoServer(port);
        server.start();

        var b = new Bootstrap();
        b.group(new NioEventLoopGroup()).channel(NioSocketChannel.class).handler(new EchoServerHandler("client"))
                .option(ChannelOption.AUTO_READ, true);
        var client = b.connect(new InetSocketAddress(port)).channel();
        client.writeAndFlush(Unpooled.wrappedBuffer("안녕 세상!".getBytes())).sync();
        Thread.sleep(100);
        server.close();
//        [main] INFO test2.EchoServer - Server listening :55555
//        [main] INFO test2.NettyTest - class io.netty.channel.socket.nio.NioSocketChannel
//        [nioEventLoopGroup-3-1] INFO test2.EchoServerHandler - Handler[server] received : 안녕 세상!
//        [nioEventLoopGroup-4-1] INFO test2.EchoServerHandler - Handler[client] received : 안녕 세상!
//        [nioEventLoopGroup-3-1] INFO test2.EchoServerHandler - Handler[server] received : 안녕 세상!
//        [nioEventLoopGroup-4-1] INFO test2.EchoServerHandler - Handler[client] received : 안녕 세상!
//        [nioEventLoopGroup-3-1] INFO test2.EchoServerHandler - Handler[server] received : 안녕 세상!
//        [nioEventLoopGroup-4-1] INFO test2.EchoServerHandler - Handler[client] received : 안녕 세상!
//        ...
    }
}
