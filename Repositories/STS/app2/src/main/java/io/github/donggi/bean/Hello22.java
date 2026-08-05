package io.github.donggi.bean;

import lombok.Data;

@Data
public class Hello22 {
    private String message;

    public String multipleMessage(int n) {
        var builder = new StringBuilder();
        for (var i = 0; i < n; ++i)
            builder.append(message);
        return builder.toString();
    }
}
