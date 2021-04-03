package tutorial.webflux.action;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.WebSession;

import reactor.core.publisher.Mono;

@RequestMapping("/DefaultSessionAction")
@Controller
public class DefaultSessionAction {

    @ResponseBody
    @GetMapping("/get")
    public Mono<Map<String, Object>> get(WebSession s) {
        return Mono.just(s.getAttributes());
    }

    @ResponseBody
    @GetMapping("/set/{key}/{value}")
    public Mono<ResponseEntity<Void>> set(@PathVariable String key, @PathVariable String value, WebSession s) {
        // attribute를 설정하기 전까진 쿠키로 설정을 요구하지 않는다.
        s.getAttributes().put(key, value);
        return Mono.just(ResponseEntity.ok().build());
    }

}
