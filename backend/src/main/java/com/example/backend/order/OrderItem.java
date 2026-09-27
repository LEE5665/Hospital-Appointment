package com.example.backend.order;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor
public class OrderItem {
    public enum Type { TEST, PROCEDURE }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @Column(nullable = false, length = 200) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Type type;
    @Column(nullable = false) private boolean active = true;
    public OrderItem(String code, String name, Type type) {
        this.code = code; this.name = name; this.type = type;
    }
}
