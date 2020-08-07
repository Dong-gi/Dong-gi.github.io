package io.github.donggi.anno;

import org.springframework.beans.factory.annotation.Autowired;
import lombok.Data;

@Data
public class Anno01 {
    @Autowired
    private String anno01Message;
}
