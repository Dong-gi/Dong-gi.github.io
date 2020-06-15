package io.github.donggi.anno;

import java.util.List;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
public class Anno08 {

    private Anno08NotInstanciated[] anno08Array;
    private List<Anno08NotInstanciated> anno08List;

    public Anno08(Anno08NotInstanciated[] anno08Array, List<Anno08NotInstanciated> anno08List) {
        this.anno08Array = anno08Array;
        this.anno08List = anno08List;
    }

}

class Anno08NotInstanciated {}