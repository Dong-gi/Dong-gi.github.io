package io.github.donggi.jpa.entity;

import java.util.Date;

import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

import io.github.donggi.jpa.enums.ApiResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_api_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logNo;
    private Long userId;
    private String requestApi;
    private String requestParam;
    @Convert(converter = ApiResultCode.Converter.class)
    private ApiResultCode apiResult;
    @Temporal(TemporalType.TIMESTAMP)
    private Date addDate;

    @PrePersist
    public void prePersist() {
        addDate = new Date();
    }
}
