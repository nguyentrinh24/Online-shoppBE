package com.project.shopapp.services.coupon;

import com.project.shopapp.dtos.coupon.CouponDTO;
import com.project.shopapp.models.Coupon;
import com.project.shopapp.repositories.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService implements ICouponService {
    private final CouponRepository couponRepository;

    @Override
    public double calculateCouponValue(String couponCode, double totalAmount) {
        Coupon coupon = couponRepository.findByCode(couponCode)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));

        if (!isValidCoupon(coupon)) {
            throw new IllegalArgumentException("Coupon is not valid");
        }

        if (totalAmount < coupon.getMinPurchaseAmount()) {
            throw new IllegalArgumentException("Total amount is less than minimum purchase amount");
        }

        double discountAmount = 0;
        if (coupon.getDiscountType().equals("PERCENTAGE")) {
            discountAmount = totalAmount * (coupon.getDiscountValue() / 100.0);
        } else if (coupon.getDiscountType().equals("FIXED_AMOUNT")) {
            discountAmount = coupon.getDiscountValue();
        }

        return Math.max(0, totalAmount - discountAmount);
    }

    private boolean isValidCoupon(Coupon coupon) {
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            LocalDate currentDate = LocalDate.now();
            LocalDate startDate = LocalDate.parse(coupon.getStartDate(), formatter);
            LocalDate endDate = LocalDate.parse(coupon.getEndDate(), formatter);

            return currentDate.isAfter(startDate) && currentDate.isBefore(endDate);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format in coupon. Expected format: yyyy-MM-dd");
        }
    }

    @Override
    @Transactional
    public Coupon createCoupon(CouponDTO couponDTO) {
        if (couponRepository.existsByCode(couponDTO.getCode())) {
            throw new RuntimeException("Coupon code already exists");
        }

        Coupon coupon = Coupon.builder()
                .code(couponDTO.getCode())
                .description(couponDTO.getDescription())
                .discountType(couponDTO.getDiscountType())
                .discountValue(couponDTO.getDiscountValue())
                .minPurchaseAmount(couponDTO.getMinPurchaseAmount())
                .startDate(couponDTO.getStartDate())
                .endDate(couponDTO.getEndDate())
                .build();
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public Coupon updateCoupon(Long id, CouponDTO couponDTO) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Coupon not found"));

        if (!coupon.getCode().equals(couponDTO.getCode()) && 
            couponRepository.existsByCode(couponDTO.getCode())) {
            throw new RuntimeException("Coupon code already exists");
        }

        coupon.setCode(couponDTO.getCode());
        coupon.setDescription(couponDTO.getDescription());
        coupon.setDiscountType(couponDTO.getDiscountType());
        coupon.setDiscountValue(couponDTO.getDiscountValue());
        coupon.setMinPurchaseAmount(couponDTO.getMinPurchaseAmount());
        coupon.setStartDate(couponDTO.getStartDate());
        coupon.setEndDate(couponDTO.getEndDate());
        return couponRepository.save(coupon);
    }

    @Override
    @Transactional
    public void deleteCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Coupon not found"));
        couponRepository.delete(coupon);
    }

    @Override
    public Coupon getCouponById(Long id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Coupon not found"));
    }

    @Override
    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    @Override
    @Transactional
    public Coupon toggleCouponStatus(Long id) {
        Coupon coupon = getCouponById(id);
        coupon.setActive(!coupon.isActive());
        return couponRepository.save(coupon);
    }
} 