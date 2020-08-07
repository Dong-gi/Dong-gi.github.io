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

    private Anno09NotInstanciated field1;
    private Anno09NotInstanciated field2;

    public Anno09(Optional<Anno09NotInstanciated> field1, @Nullable Anno09NotInstanciated field2) {
        this.field1 = field1.orElse(null);
        this.field2 = field2;
    }

}

class Anno09NotInstanciated {}