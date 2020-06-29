package io.github.donggi.anno;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.text.MessageFormat;

import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.Payload;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = Test2ConstraintValidator.class)
public @interface Test2Constraint {
    String message() default "";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

class Test2ConstraintValidator implements ConstraintValidator<Test2Constraint, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        var result = value.length() % 2 == 0;
        if (!result) {
            context.disableDefaultConstraintViolation();
            if (context.getDefaultConstraintMessageTemplate() != null && context.getDefaultConstraintMessageTemplate().length() > 0)
                context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate()).addBeanNode().addConstraintViolation();
            else
                context.buildConstraintViolationWithTemplate(MessageFormat.format("문자열 길이는 짝수여야 합니다. But {0}", value)).addBeanNode().addConstraintViolation();
        }
        return result;
    }

}