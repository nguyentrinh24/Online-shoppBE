package com.project.shopapp.services.coupon;

import com.project.shopapp.dtos.coupon.CouponDTO;
import com.project.shopapp.models.Coupon;

import java.util.List;

public interface ICouponService {
    double calculateCouponValue(String couponCode, double totalAmount);
    
    Coupon createCoupon(CouponDTO couponDTO);
    
    Coupon getCouponById(Long id);
    
    Coupon updateCoupon(Long id, CouponDTO couponDTO);
    
    void deleteCoupon(Long id);
    
    List<Coupon> getAllCoupons();
    
    Coupon toggleCouponStatus(Long id);
} 