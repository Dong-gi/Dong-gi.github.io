package io.github.donggi.jpa.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Embeddable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemInfo3Key implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Column(name="item_id")
    private Long itemId;
    @Column(name="seq_no")
    private Integer seqNo;
}
