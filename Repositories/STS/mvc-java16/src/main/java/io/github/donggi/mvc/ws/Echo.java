package io.github.donggi.mvc.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class Echo extends TextWebSocketHandler {
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.sendMessage(message);
        log.debug(message.getPayload());
    }
/*
20:36:48.800 [http-nio-8080-exec-10] DEBUG io.github.donggi.mvc.ws.Echo - SESSION- 2 : donggi
20:36:48.940 [http-nio-8080-exec-1] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java16/app/ws/echo/info?t=1598009808906", parameters={masked}, headers={masked} in DispatcherServlet 'app'
20:36:48.942 [http-nio-8080-exec-1] DEBUG o.s.web.servlet.DispatcherServlet - Completed 200 OK, headers={masked}
20:36:48.950 [http-nio-8080-exec-3] TRACE o.s.web.servlet.DispatcherServlet - GET "/mvc-java16/app/ws/echo/673/2bnilahl/websocket", parameters={}, headers={masked} in DispatcherServlet 'app'
20:36:48.952 [http-nio-8080-exec-3] TRACE o.s.w.s.s.s.DefaultHandshakeHandler - Upgrading to WebSocket, subProtocol=null, extensions=[]
20:36:48.953 [http-nio-8080-exec-3] DEBUG o.s.web.servlet.DispatcherServlet - Completed 101 SWITCHING_PROTOCOLS, headers={masked}
20:36:48.959 [http-nio-8080-exec-4] DEBUG io.github.donggi.mvc.ws.Echo - Hello World
20:36:48.960 [http-nio-8080-exec-4] DEBUG io.github.donggi.mvc.ws.Echo - 안녕 세상
*/
}
