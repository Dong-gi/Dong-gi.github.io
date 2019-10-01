package io.github.donggi.request;

import java.util.List;

import lombok.Data;

@Data
public class BulkLoginRequest {
    private List<LoginRequest> loginRequests;
}
