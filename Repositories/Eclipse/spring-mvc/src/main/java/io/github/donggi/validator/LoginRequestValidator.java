package io.github.donggi.validator;

import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import io.github.donggi.request.LoginRequest;

public class LoginRequestValidator implements Validator{

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz.equals(LoginRequest.class);
    }

    @Override
    public void validate(Object target, Errors errors) {
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "email", "email is empty");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "password", "password is empty");
    }

}
