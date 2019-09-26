package net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Scanner;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SocketTest {

    static ServerSocket echoServer;
    static int port = 36001;

    @BeforeAll
    static void before() throws IOException {
        echoServer = new ServerSocket(port);
        new Thread(() -> {
            while(!echoServer.isClosed()) {
                try {
                    new Thread(new ServerWorker(echoServer.accept())).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        System.out.println("[server] Start!");
    }

    @Test
    void test() throws UnknownHostException, IOException, InterruptedException {
        var client = new Socket();
        client.connect(new InetSocketAddress("localhost", port));
        System.out.println("[client] Start!");

        var scanner = new Scanner(System.in);
        var in = new BufferedReader(new InputStreamReader(client.getInputStream()));
        var out = new PrintWriter(client.getOutputStream(), true);

        final var messages = new String[] { "안녕 세상!", "메시지 2", "메시지 3" };
        for(var msg : messages) {
            Thread.sleep(1000);
            System.out.println("[client] Sending... : " + msg);
            out.println(msg);
            for(var j = 0; j < 10; ++j) {
                if(in.ready()) {
                    System.out.println("[client] Received : " + in.readLine());
                    break;
                }
                Thread.sleep(100);
            }
        }
        client.close();
        echoServer.close();
    }

}

class ServerWorker implements Runnable {
    private Socket client;

    ServerWorker(Socket client) {
        this.client = client;
        System.out.println("[server] New client : " + client);
    }

    @Override
    public void run() {
        try {
            var in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            var out = new PrintWriter(client.getOutputStream(), true);
            System.out.println("[server] Waiting...");
            while(!client.isClosed()) {
                if(in.ready()) {
                    var msg = in.readLine();
                    System.out.println("[server] Echo : " + msg);
                    out.println(msg);
                }
                Thread.sleep(100);
            }
            System.out.println("[server] Closed : " + client);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/*
[server] Start!
[client] Start!
[server] New client : Socket[addr=/127.0.0.1,port=63052,localport=35001]
[server] Waiting...
[client] Write Something : 1 + 1 = ?
[client] Sending... : 1 + 1 = ?
[server] Echo : 1 + 1 = ?
[client] Received : 1 + 1 = ?
[client] Write Something : Answer is 2.
[client] Sending... : Answer is 2.
[server] Echo : Answer is 2.
[client] Received : Answer is 2.
[client] Write Something : Right
[client] Sending... : Right
[server] Echo : Right
[client] Received : Right
*/