package tutorial.webflux.action;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import reactor.core.publisher.Mono;

@RequestMapping("/CookieAndSessionAction")
@Controller
public class CookieAndSessionAction {

    @ResponseBody
    @GetMapping("/get")
    public Mono<Map<String, Object>> get(ServerWebExchange exchange, WebSession s) {
        var m = new HashMap<>(s.getAttributes());
        exchange.getRequest().getCookies().forEach((key, cookie) -> {
            m.put(key, cookie.toString());
        });
        return Mono.just(m);
    }

    @ResponseBody
    @GetMapping("/set/{key}/{value}")
    public Mono<ResponseEntity<Void>> set(@PathVariable String key, @PathVariable String value,
            ServerWebExchange exchange, WebSession s) {
        exchange.getResponse()
                .addCookie(ResponseCookie.from(key, value).path("/").httpOnly(true).sameSite("Strict").build());
        // attribute를 설정하기 전까진 쿠키로 설정을 요구하지 않는다.
        s.getAttributes().put(key, value);
        return Mono.just(ResponseEntity.ok().build());
    }

}
