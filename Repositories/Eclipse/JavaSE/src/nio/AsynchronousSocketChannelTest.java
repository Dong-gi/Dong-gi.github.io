package nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;


class AsynchronousSocketChannelTest {

    static final int BUF_SIZE = 1024;
    static final int PORT = 35003;

    AsynchronousServerSocketChannel serverChannel;
    AsynchronousSocketChannel clientChannel;


    @Test
    void test() throws IOException, InterruptedException, ExecutionException {
        serverChannel = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(PORT));
        serverChannel.setOption(StandardSocketOptions.SO_RCVBUF, BUF_SIZE);
        serverChannel.accept(null, new ServerHandler());

        clientChannel = AsynchronousSocketChannel.open();
        clientChannel.connect(new InetSocketAddress("localhost", PORT)).get();
        final var RCV_BUF = ByteBuffer.allocate(BUF_SIZE);
        clientChannel.read(RCV_BUF, null, new ClientHandler(RCV_BUF));

        final var messages = new String[] {"안녕 세상!", "메시지 2", "메시지 3"};
        for(var message : messages) {
            Thread.sleep(1000);
            System.out.println("CLIENT SENDING... : " + message);
            final var charBuffer = CharBuffer.wrap(message);
            final var byteBuffer = ByteBuffer.allocate(charBuffer.length() * Character.BYTES);
            byteBuffer.asCharBuffer().put(charBuffer);
            clientChannel.write(byteBuffer);
        }
        serverChannel.close();
        clientChannel.close();
    }

/*
CURRENT CHANNEL : sun.nio.ch.WindowsAsynchronousSocketChannelImpl[connected local=/127.0.0.1:35003 remote=/127.0.0.1:64137]
CLIENT SENDING... : 안녕 세상!
SERVER RECEIVED, AND SENDING... : 안녕 세상!
CURRENT CHANNEL : sun.nio.ch.WindowsAsynchronousSocketChannelImpl[connected local=/127.0.0.1:35003 remote=/127.0.0.1:64137]
CLIENT RECEIVED : 안녕 세상!
CLIENT SENDING... : 메시지 2
SERVER RECEIVED, AND SENDING... : 메시지 2
CURRENT CHANNEL : sun.nio.ch.WindowsAsynchronousSocketChannelImpl[connected local=/127.0.0.1:35003 remote=/127.0.0.1:64137]
CLIENT RECEIVED : 메시지 2
CLIENT SENDING... : 메시지 3
SERVER RECEIVED, AND SENDING... : 메시지 3
CURRENT CHANNEL : sun.nio.ch.WindowsAsynchronousSocketChannelImpl[connected local=/127.0.0.1:35003 remote=/127.0.0.1:64137]
CHANNEL CLOSED : sun.nio.ch.WindowsAsynchronousSocketChannelImpl[connected local=/127.0.0.1:35003 remote=/127.0.0.1:64137]
*/

    class ServerHandler implements CompletionHandler<AsynchronousSocketChannel, Void> {

        @Override
        public void completed(AsynchronousSocketChannel result, Void attachment) {
            serverChannel.accept(null, this);
            try {
                handle(result);
            } catch (Exception e) {
                System.out.println("CHANNEL CLOSED : " + result);
            }
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            try {
                serverChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void handle(final AsynchronousSocketChannel channel) throws InterruptedException, ExecutionException {
            while(channel.isOpen()) {
                var buffer = ByteBuffer.allocate(BUF_SIZE);
                System.out.println("CURRENT CHANNEL : " + channel);

                channel.read(buffer).get();
                buffer.position(0);
                System.out.println("SERVER RECEIVED, AND SENDING... : " + buffer.asCharBuffer().toString().trim());
                buffer.position(0);
                channel.write(buffer);
            }
        }
    }

    class ClientHandler implements CompletionHandler<Integer, Void> {
        final ByteBuffer RCV_BUF;

        ClientHandler(ByteBuffer receiveBuffer) {
            RCV_BUF = receiveBuffer;
        }

        @Override
        public void completed(Integer result, Void attachment) {
            RCV_BUF.position(0);
            final var message = RCV_BUF.asCharBuffer().toString();
            RCV_BUF.position(0);
            clientChannel.read(RCV_BUF, null, this);
            System.out.println("CLIENT RECEIVED : " + message.trim());
        }

        @Override
        public void failed(Throwable exc, Void attachment) {
            try {
                clientChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
