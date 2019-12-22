package io.github.donggi.websocket;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;

import io.github.donggi.config.ServerEndpointConfigurator;
import io.github.donggi.controller.SleepController;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Component
@ServerEndpoint(value = "/ws/echo/{name}", configurator = ServerEndpointConfigurator.class)
public class Echo {
    @Autowired
    private SleepController sleepController;
    
    private static final Map<String, Client> SESSION_ID_TO_CLIENT = new HashMap<>();
    
    @Getter
    @AllArgsConstructor
    private static class Client {
        private final Session session;
        private final String name;
    }
    
    @PostConstruct
    public void init(){
        SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
    }
    
    @OnOpen
    public void onOpen(Session session, @PathParam("name") String name) {
        System.out.printf("SESSION+ %s : %s\n", session.getId(), name);
        SESSION_ID_TO_CLIENT.put(session.getId(), new Client(session, name));
    }
    
    @OnClose
    public void onClose(Session session) {
        System.out.printf("SESSION- %s : %s\n", session.getId(), SESSION_ID_TO_CLIENT.get(session.getId()).getName());
        SESSION_ID_TO_CLIENT.remove(session.getId());
    }
    
    @OnError
    public void onError(Throwable t) {
        t.printStackTrace();
    }
    
    @OnMessage
    public void onMessage(Session session, String msg) throws IOException {
        System.out.printf("MESSAGE %s : %s\n", SESSION_ID_TO_CLIENT.get(session.getId()).getName(), msg);
        sleepController.justSleep();
        session.getBasicRemote().sendText(msg);
    }
    /*
     SESSION+ 0 : DK
     MESSAGE DK : hello
     Sleep start
     MESSAGE DK : 안녕
     Sleep start
     Sleep end
     Sleep end
     SESSION- 0 : DK
     */
}
