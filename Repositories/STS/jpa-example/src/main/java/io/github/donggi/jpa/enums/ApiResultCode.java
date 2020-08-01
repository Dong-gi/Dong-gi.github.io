package io.github.donggi.jpa.enums;

import java.util.Map;

import javax.persistence.AttributeConverter;

import io.github.donggi.jpa.utils.EnumUtil;

public enum ApiResultCode implements EnumValue {
    /** 정상 */
    OK(200),
    /** 입력 불량 */
    BAD_REQUEST(400),
    /** 알 수 없는 서버 에러 */
    INTERNAL_ERROR(500),
    ;

    private final int value;
    private static final Map<Integer, ApiResultCode> MAP = EnumUtil.asMap(OK);


    //@com.fasterxml.jackson.annotation.JsonCreator
    public static ApiResultCode valueOf(int value) {
        return MAP.get(value);
    }

    private ApiResultCode(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    public static class Converter implements AttributeConverter<ApiResultCode, Integer> {
        @Override
        public Integer convertToDatabaseColumn(ApiResultCode attribute) {
            return attribute.getValue();
        }

        @Override
        public ApiResultCode convertToEntityAttribute(Integer dbData) {
            return valueOf(dbData);
        }
    }
}