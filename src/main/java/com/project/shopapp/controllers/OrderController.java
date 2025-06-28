package com.project.shopapp.controllers;

import com.project.shopapp.components.LocalizationUtils;
import com.project.shopapp.dtos.*;
import com.project.shopapp.filters.AuthJwtToken;
import com.project.shopapp.models.Order;
import com.project.shopapp.models.User;
import com.project.shopapp.repositories.OrderRepository;
import com.project.shopapp.responses.Order.OrderListResponse;
import com.project.shopapp.responses.Order.OrderResponse;
import com.project.shopapp.responses.User.UserResponse;
import com.project.shopapp.services.Order.IOrderService;
import com.project.shopapp.utils.MessageKeys;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.bind.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${api.prefix}/orders")
@RequiredArgsConstructor
public class OrderController {
    private final IOrderService orderService;
    private final LocalizationUtils localizationUtils;
    private final OrderRepository orderRepository;

    @PostMapping("")
    @Transactional
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody OrderDTO orderDTO,
            BindingResult result
    ) {
        try {
            if(result.hasErrors()) {
                List<String> errorMessages = new ArrayList<>();
                for (FieldError error : result.getFieldErrors()) {
                    errorMessages.add(error.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(errorMessages);
            }

            // Validate cart items
            if (orderDTO.getCartItems() == null || orderDTO.getCartItems().isEmpty()) {
                return ResponseEntity.badRequest().body("Cart items cannot be empty");
            }

            // Validate total money
            if (orderDTO.getTotalMoney() <= 0) {
                return ResponseEntity.badRequest().body("Total money must be greater than 0");
            }


            Order orderResponse = orderService.createOrder(orderDTO);

            return ResponseEntity.ok()
                    .body(orderResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/user/{user_id}") // Thêm user_id vào path
    public ResponseEntity<?> getOrders(@Valid @PathVariable("user_id") Long userId,
                                       @RequestHeader("Authorization") String authHeader) {
        try {
            String token = AuthJwtToken.extractToken(authHeader);
            List<Order> orders = orderService.findByUserId(userId);
            List<OrderResponse> orderResponses = orders.stream()
                    .map(OrderResponse::fromOrder)
                    .toList();
            return ResponseEntity.ok()
                    .header(token)
                    .body(orderResponses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/user/{user_id}/orders")
    public ResponseEntity<?> getUserOrders(
            @Valid @PathVariable("user_id") Long userId,
            @RequestParam(defaultValue = "", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestHeader("Authorization") String authHeader) {
        try {
            String token = AuthJwtToken.extractToken(authHeader);
            PageRequest pageRequest = PageRequest.of(page, limit, Sort.by("orderDate").descending());
            Page<Order> orderPage = orderService.getUserOrders(userId, keyword, pageRequest);
            Page<OrderResponse> orderResponsePage = orderPage.map(OrderResponse::fromOrder);
            
            OrderListResponse orderListResponse = OrderListResponse.builder()
                    .orders(orderResponsePage.getContent())
                    .totalPages(orderResponsePage.getTotalPages())
                    .build();
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .body(orderListResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@Valid @PathVariable("id") Long orderId,
                                      @RequestHeader(name = "Authorization") String authHeader) {
        try {
            String token  = AuthJwtToken.extractToken(authHeader);
            Order existingOrder = orderService.getOrder(orderId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .body(existingOrder);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Transactional
    //PUT http://localhost:8088/api/v1/orders/2
    //công việc của admin
    public ResponseEntity<?> updateOrder(
            @Valid @PathVariable long id,
            @Valid @RequestBody OrderDTO orderDTO,
            @RequestHeader(name = "Authorization") String authorization) {

        try {
            String token = AuthJwtToken.extractToken(authorization);
            Order order = orderService.updateOrder(id, orderDTO);

            return ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION,"BEAER "+token)
                    .body(order);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteOrder(@Valid @PathVariable Long id) {

        //xóa mềm => cập nhật trường active = false
        orderService.deleteOrder(id);

        String result = localizationUtils.getLocalizedMessage(
                MessageKeys.DELETE_ORDER_SUCCESSFULLY, id);
        return ResponseEntity.ok()

                .body(result);
    }

    @GetMapping("/get-orders-by-keyword")
    public ResponseEntity<OrderListResponse> getOrdersByKeyword(
            @RequestParam(defaultValue = "", required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit

    ) {


        // Tạo Pageable từ thông tin trang và giới hạn
        PageRequest pageRequest = PageRequest.of(
                page, limit,
                //Sort.by("createdAt").descending()
                Sort.by("id").ascending()
        );
        Page<OrderResponse> orderPage = orderService
                                        .getOrdersByKeyword(keyword, pageRequest)
                                        .map(OrderResponse::fromOrder);
        // Lấy tổng số trang
        int totalPages = orderPage.getTotalPages();
        List<OrderResponse> orderResponses = orderPage.getContent();
       OrderListResponse orderResponse=OrderListResponse
                .builder()
                .orders(orderResponses)
                .totalPages(totalPages)
                .build();
       return ResponseEntity.ok()
               .body(orderResponse);
    }

    @GetMapping("/latest")
    public ResponseEntity<?> getLatestOrder(
            @AuthenticationPrincipal User userDetails,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader
    ) {
        String token = AuthJwtToken.extractToken(authHeader);

        //  Lấy đơn hàng mới nhất
        Order order = orderRepository
                .findTopByUserIdOrderByOrderDateDesc(userDetails.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng mới nhất cho user id=" + userDetails.getId()
                ));
        //Build response
        Map<String,Object> body = Map.of(
                "token",token,
                "user", UserResponse.fromUser(userDetails),
                "order", OrderResponse.fromOrder(order)
        );

        return ResponseEntity.ok()
                .body(body);
    }
}
