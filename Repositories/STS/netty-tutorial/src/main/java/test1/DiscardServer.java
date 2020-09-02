package test1;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiscardServer implements AutoCloseable {

    private int port;
    private Channel ch;
    private NioEventLoopGroup bossGroup;
    private NioEventLoopGroup workerGroup;

    public DiscardServer(int port) {
        this.port = port;
        bossGroup = new NioEventLoopGroup(); // (1)
        workerGroup = new NioEventLoopGroup();
    }

    public Channel start() {
        var b = new ServerBootstrap(); // (2)
        b.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class) // (3)
        .childHandler(new ChannelInitializer<SocketChannel>() { // (4)
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline()
                    .addLast(new DiscardServerHandler());
            }
        })
        .option(ChannelOption.SO_BACKLOG, 128)          // (5)
        .childOption(ChannelOption.SO_KEEPALIVE, true); // (6)

        // Bind and start to accept incoming connections.
        ChannelFuture f = null;
        try {
            f = b.bind(port).sync();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } // (7)
        log.info("Server listening :{}", port);

        // Wait until the server socket is closed.
        // In this example, this does not happen, but you can do that to gracefully
        // shut down your server.
        ch = f.channel();
        return ch;
    }

    @Override
    public void close() throws Exception {
        ch.close().sync().addListener(f -> {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        });
    } 
}
