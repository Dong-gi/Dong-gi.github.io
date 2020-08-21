package io.github.donggi.mvc.request;

import javax.validation.constraints.Size;

import lombok.Data;

@Data
public class LoginRequest {
    private String email;
    @Size(max = 3)
    private String password;
}
