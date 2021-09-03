package link4.joy.action;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import link4.joy.res.StaticResponse;

@RestController
public class DefaultAction {

    @RequestMapping(path = "/**", method = {
        RequestMethod.GET, RequestMethod.POST, RequestMethod.DELETE,
        RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH, RequestMethod.PUT, RequestMethod.TRACE
    })
    public ResponseEntity<Object> default404() { return StaticResponse.NOT_FOUND; }

}
