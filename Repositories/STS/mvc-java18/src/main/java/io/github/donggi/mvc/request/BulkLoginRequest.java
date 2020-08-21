package io.github.donggi.mvc.request;

import java.util.List;

import javax.validation.Valid;

import lombok.Data;

@Data
public class BulkLoginRequest {
    @Valid
    private List<LoginRequest> loginRequests;
}
