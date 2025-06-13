package com.project.shopapp.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coupons")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Coupon extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "description")
    private String description;

    @Column(name = "discount_type", nullable = false)
    private String discountType; // PERCENTAGE or FIXED_AMOUNT

    @Column(name = "discount_value", nullable = false)
    private Double discountValue;

    @Column(name = "min_purchase_amount", nullable = false)
    private Double minPurchaseAmount;

    @Column(name = "start_date", nullable = false)
    private String startDate;

    @Column(name = "end_date", nullable = false)
    private String endDate;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "coupon", cascade = CascadeType.ALL)
    private java.util.List<CouponCondition> conditions;
} 