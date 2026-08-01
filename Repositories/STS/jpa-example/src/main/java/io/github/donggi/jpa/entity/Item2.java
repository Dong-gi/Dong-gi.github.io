package io.github.donggi.jpa.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "jpa_item2")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Item2 {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long itemId;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "price", column = @Column(name = "ko_price")),
        @AttributeOverride(name = "discount", column = @Column(name = "ko_discount"))
    })
    private Price koPrice;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "price", column = @Column(name = "jp_price")),
        @AttributeOverride(name = "discount", column = @Column(name = "jp_discount"))
    })
    private Price jpPrice;
}
