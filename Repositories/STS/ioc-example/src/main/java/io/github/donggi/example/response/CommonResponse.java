package io.github.donggi.example.response;

import lombok.Data;

@Data
public class CommonResponse {
    private int perApp;
    private int perRequest;
    private int perSession;
}
