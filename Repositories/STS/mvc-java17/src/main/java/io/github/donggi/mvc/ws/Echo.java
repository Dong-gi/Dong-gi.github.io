package io.github.donggi.mvc.ws;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.springframework.stereotype.Component;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Component
@ServerEndpoint(value = "/ws/echo/{name}")
@Slf4j
public class Echo {

    private static final Map<String, Client> SESSION_TO_CLIENT = new HashMap<>();


    @Getter
    @AllArgsConstructor
    private static class Client {
        private final Session session;
        private final String name;
    }


    @OnOpen
    public void onOpen(Session session, @PathParam("name") String name) {
        SESSION_TO_CLIENT.put(session.getId(), new Client(session, name));
        log.debug("SESSION+ {} : {}", session.getId(), name);
    }
    @OnClose
    public void onClose(Session session) {
        log.debug("SESSION- {} : {}", session.getId(), SESSION_TO_CLIENT.get(session.getId()).getName());
        SESSION_TO_CLIENT.remove(session.getId());
    }
    @OnError
    public void onError(Throwable t) {
        log.error("error", t);
    }
    @OnMessage
    public void onMessage(Session session, String msg) throws IOException {
        session.getBasicRemote().sendText(msg);
        log.debug("MESSAGE {} : {}", SESSION_TO_CLIENT.get(session.getId()).getName(), msg);
    }
/*
20:39:15.062 [http-nio-8080-exec-5] DEBUG io.github.donggi.mvc.ws.Echo - SESSION+ 5 : donggi
20:39:15.104 [http-nio-8080-exec-6] DEBUG io.github.donggi.mvc.ws.Echo - MESSAGE donggi : Hello World
20:39:15.105 [http-nio-8080-exec-6] DEBUG io.github.donggi.mvc.ws.Echo - MESSAGE donggi : 안녕 세상
20:39:17.103 [http-nio-8080-exec-7] DEBUG io.github.donggi.mvc.ws.Echo - SESSION- 5 : donggi
*/
}
