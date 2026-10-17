package vn.sunvibe.property.core.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WardEntity {

    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Column(name = "province_code", nullable = false, length = 10)
    private String provinceCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "type", nullable = false, length = 20)
    private String type;
}
