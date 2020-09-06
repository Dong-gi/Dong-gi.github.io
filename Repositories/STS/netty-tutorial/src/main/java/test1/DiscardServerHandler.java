package test1;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DiscardServerHandler extends ChannelInboundHandlerAdapter {
    private String name;
    
    public DiscardServerHandler(String name) {
        this.name = String.valueOf(name);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        var buf = ByteBuf.class.cast(msg);
        try {
            log.info("Handler[{}] received : {}", name, buf.toString(CharsetUtil.UTF_8));
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }
}
