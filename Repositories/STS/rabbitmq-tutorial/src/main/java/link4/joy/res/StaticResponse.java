package link4.joy.res;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class StaticResponse {
    public static final ResponseEntity<Object> BAD_GATEWAY = new ResponseEntity<>(null, HttpStatus.BAD_GATEWAY);
    public static final ResponseEntity<Object> OK = new ResponseEntity<>(null, HttpStatus.OK);
    public static final ResponseEntity<Object> NOT_FOUND = new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
    public static final ResponseEntity<Object> NOT_MODIFIED = new ResponseEntity<>(null, HttpStatus.NOT_MODIFIED);
}
