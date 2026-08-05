package io.github.donggi.mvc.validator;

import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import io.github.donggi.mvc.request.LoginRequest;

public class LoginRequestValidator implements Validator{

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz.equals(LoginRequest.class);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "email", "is.empty");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "is.empty");
    }

}
