package io.github.donggi.anno;

import java.util.Optional;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
public class Anno09 {

    private Anno09NotInstanciated anno09field1;
    private Anno09NotInstanciated anno09field2;

    public Anno09(Optional<Anno09NotInstanciated> anno09field1, @Nullable Anno09NotInstanciated anno09field2) {
        this.anno09field1 = anno09field1.orElse(null);
        this.anno09field2 = anno09field2;
    }

}

class Anno09NotInstanciated {}