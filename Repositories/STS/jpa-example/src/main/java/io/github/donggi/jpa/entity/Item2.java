package io.github.donggi.jpa.entity;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
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
