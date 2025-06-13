package com.project.shopapp.services.Order;

import com.project.shopapp.dtos.Order.OrderDTO;
import com.project.shopapp.dtos.Order.OrderDetailDTO;
import com.project.shopapp.dtos.Order.OrderWithDetailsDTO;
import com.project.shopapp.dtos.Categories.CartItemDTO;
import com.project.shopapp.exceptions.DataNotFoundException;
import com.project.shopapp.exceptions.InvalidParamException;
import com.project.shopapp.models.*;
import com.project.shopapp.repositories.OrderDetailRepository;
import com.project.shopapp.repositories.OrderRepository;
import com.project.shopapp.repositories.ProductRepository;
import com.project.shopapp.repositories.UserRepository;
import com.project.shopapp.services.coupon.CouponService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderService implements IOrderService {
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final CouponService couponService;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public Order createOrder(OrderDTO orderDTO) throws Exception {

        User user = userRepository
                .findById(orderDTO.getUserId())
                .orElseThrow(() -> new DataNotFoundException("Cannot find user with id: "+orderDTO.getUserId()));
        

        float totalMoney = orderDTO.getTotalMoney();
        if (orderDTO.getCouponCode() != null && !orderDTO.getCouponCode().isEmpty()) {
            try {
                totalMoney = (float) couponService.calculateCouponValue(orderDTO.getCouponCode(), totalMoney);
            } catch (Exception e) {
                throw new DataNotFoundException("Invalid coupon code: " + e.getMessage());
            }
        }

        //convert orderDTO => Order
        modelMapper.typeMap(OrderDTO.class, Order.class)
                .addMappings(mapper -> mapper.skip(Order::setId));
        
        Order order = new Order();
        modelMapper.map(orderDTO, order);
        order.setUser(user);
        order.setOrderDate(LocalDate.now());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalMoney(totalMoney);


        LocalDate shippingDate = orderDTO.getShippingDate() == null
                ? LocalDate.now() : orderDTO.getShippingDate();
        if (shippingDate.isBefore(LocalDate.now())) {
            throw new DataNotFoundException("Date must be at least today !");
        }
        order.setShippingDate(shippingDate);
        order.setActive(true);
        

        Order savedOrder = orderRepository.save(order);
        

        if (orderDTO.getCartItems() != null && !orderDTO.getCartItems().isEmpty()) {
            List<OrderDetail> orderDetails = new ArrayList<>();
            for (CartItemDTO cartItem : orderDTO.getCartItems()) {
                Product product = productRepository.findById(cartItem.getProductId())
                        .orElseThrow(() -> new DataNotFoundException(
                                "Cannot find product with id: " + cartItem.getProductId()));
                
                // Validate quantity
                if (cartItem.getQuantity() > product.getQuantity()) {
                    throw new InvalidParamException(
                            String.format("Order quantity (%d) exceeds available quantity (%d) for product: %s",
                                    cartItem.getQuantity(),
                                    product.getQuantity(),
                                    product.getName())
                    );
                }
                
                // Update product quantity
                product.setQuantity(product.getQuantity() - cartItem.getQuantity());
                product.setStock_quantity(product.getStock_quantity() - cartItem.getQuantity());
                productRepository.save(product);
                
                // Create order detail
                OrderDetail orderDetail = OrderDetail.builder()
                        .order(savedOrder)
                        .product(product)
                        .numberOfProducts(cartItem.getQuantity())
                        .price(product.getPrice())
                        .totalMoney(product.getPrice() * cartItem.getQuantity())
                        .build();
                
                orderDetails.add(orderDetail);
            }
            
            // Save all order details
            orderDetailRepository.saveAll(orderDetails);
            savedOrder.setOrderDetails(orderDetails);
        }
        
        return savedOrder;
    }
    @Transactional
    public Order updateOrderWithDetails(OrderWithDetailsDTO orderWithDetailsDTO) {
        modelMapper.typeMap(OrderWithDetailsDTO.class, Order.class)
                .addMappings(mapper -> mapper.skip(Order::setId));
        Order order = new Order();
        modelMapper.map(orderWithDetailsDTO, order);
        Order savedOrder = orderRepository.save(order);

        for (OrderDetailDTO orderDetailDTO : orderWithDetailsDTO.getOrderDetailDTOS()) {
            //orderDetail.setOrder(OrderDetail);
        }

        List<OrderDetail> savedOrderDetails = orderDetailRepository.saveAll(order.getOrderDetails());

        savedOrder.setOrderDetails(savedOrderDetails);

        return savedOrder;
    }
    @Override
    public Order getOrder(Long id) {
        Order selectedOrder = orderRepository.findById(id).orElse(null);
        return selectedOrder;
    }

    @Override
    @Transactional
    public Order updateOrder(Long id, OrderDTO orderDTO)
            throws DataNotFoundException {
        Order order = orderRepository.findById(id).orElseThrow(() ->
                new DataNotFoundException("Cannot find order with id: " + id));
        User existingUser = userRepository.findById(
                orderDTO.getUserId()).orElseThrow(() ->
                new DataNotFoundException("Cannot find user with id: " + id));
        // bảng ánh xạ riêng để kiểm soát việc ánh xạ
        modelMapper.typeMap(OrderDTO.class, Order.class)
                .addMappings(mapper -> mapper.skip(Order::setId));
        // Cập nhật các trường của đơn hàng từ orderDTO
        modelMapper.map(orderDTO, order);
        order.setUser(existingUser);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Long id) {
        Order order = orderRepository.findById(id).orElse(null);
        if(order != null) {
            order.setActive(false);
            orderRepository.save(order);
        }
    }
    @Override
    public List<Order> findByUserId(Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @Override
    public Page<Order> getOrdersByKeyword(String keyword, Pageable pageable) {
        return orderRepository.findByKeyword(keyword, pageable);
    }
}
