package io.github.donggi.anno;

import java.util.List;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
public class Anno08 {

    private Anno08NotInstanciated[] arr;
    private List<Anno08NotInstanciated> list;

    public Anno08(Anno08NotInstanciated[] arr, List<Anno08NotInstanciated> list) {
        this.arr = arr;
        this.list = list;
    }

}

class Anno08NotInstanciated {}