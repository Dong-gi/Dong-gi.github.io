package io.github.donggi.anno;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.ValidationUtils;
import org.springframework.validation.Validator;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
class Anno29Config {
    @Autowired
    private ApplicationContext context;

    @Bean
    public MessageSource messageSource() {
        var source = new ResourceBundleMessageSource();
        source.setDefaultEncoding("UTF-8");
        source.setBasenames("io/github/donggi/anno/anno29");
        return source;
    }
    @Bean
    public Anno29DataValidator anno29DataValidator() {
        return new Anno29DataValidator();
    }
    @Bean
    public Anno29Validator anno29Validator() {
        return new Anno29Validator();
    }
    @Bean
    public Anno29Data anno29Data() {
        var d = new Anno29Data();
        d.setName("my name");
        return d;
    }
    @Bean
    public Anno29 anno29() {
        var o = new Anno29();
        o.setData(anno29Data());
        var e = new BeanPropertyBindingResult(o, "anno29");
        anno29Validator().validate(o, e);
        log.info(e.toString());
        e.getAllErrors().forEach(x -> {
            String matchCode = null;
            String msg = null;
            for (var code : x.getCodes()) {
                msg = context.getMessage(code, null, null, Locale.KOREAN);
                if (msg != null) {
                    matchCode = code;
                    break;
                }
            }
            log.info("{} message : {}", matchCode, msg);
        });
        return o;
    }
}

@Data
class Anno29Data {
    String name;
    String email;
}

@Data
public class Anno29 {
    private Anno29Data data;
}

class Anno29DataValidator implements Validator {
    @Override
    public boolean supports(Class<?> clazz) {
        return Anno29Data.class.isAssignableFrom(clazz);
    }
    @Override
    public void validate(Object target, Errors errors) {
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "name", "is.empty");
        ValidationUtils.rejectIfEmptyOrWhitespace(errors, "email", "is.empty");
    }
}

class Anno29Validator implements Validator {
    @Autowired
    private Anno29DataValidator dataValidator; 

    @Override
    public boolean supports(Class<?> clazz) {
        return Anno29.class.isAssignableFrom(clazz);
    }
    @Override
    public void validate(Object target, Errors errors) {
        var o = (Anno29) target;
        if (o.getData() == null)
            errors.rejectValue("data", "is.empty");
        errors.pushNestedPath("data");
        ValidationUtils.invokeValidator(dataValidator, o.getData(), errors);
    }
}