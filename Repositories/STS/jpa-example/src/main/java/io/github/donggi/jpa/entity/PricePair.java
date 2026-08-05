package io.github.donggi.jpa.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PricePair {
    @AttributeOverrides({
        @AttributeOverride(name = "price", column = @Column(name = "new_price")),
        @AttributeOverride(name = "discount", column = @Column(name = "new_discount"))
    })
    private Price newPrice;
    @AttributeOverrides({
        @AttributeOverride(name = "price", column = @Column(name = "old_price")),
        @AttributeOverride(name = "discount", column = @Column(name = "old_discount"))
    })
    private Price oldPrice;
}
