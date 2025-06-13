package com.project.shopapp.controllers;

import com.project.shopapp.Producers.MessageProducer;
import com.project.shopapp.components.LocalizationUtils;
import com.project.shopapp.dtos.Order.OrderDTO;
import com.project.shopapp.filters.AuthJwtToken;
import com.project.shopapp.models.Order;
import com.project.shopapp.models.User;
import com.project.shopapp.repositories.OrderRepository;
import com.project.shopapp.responses.*;
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
    private final MessageProducer messageProducer;
    private final OrderRepository orderRepository;


    @PostMapping("")
    public ResponseEntity<?> createOrder(
            @Valid @RequestBody OrderDTO orderDTO,
            BindingResult result
    ) {
        try {
            if(result.hasErrors()) {
                List<String> err = new ArrayList<>();
                for (FieldError error : result.getFieldErrors()) {
                    err.add(error.getDefaultMessage());
                }
                return ResponseEntity.badRequest().body(err);
            }


            Order orderResponse = orderService.createOrder(orderDTO);
            
            // processing queue
            messageProducer.sendOrderMessage("New order created: " + orderResponse.getId());
            // Send notification
            messageProducer.sendNotificationMessage("Order created successfully for user: " + orderResponse.getUser().getId());

            return ResponseEntity.ok()
                    .body(orderResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/user/{user_id}") //
    //GET http://localhost:8088/api/v1/orders/user/4
    public ResponseEntity<?> getOrders(@Valid @PathVariable("user_id") Long userId) {
        try {
            List<Order> orders = orderService.findByUserId(userId);
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    //GET http://localhost:8088/api/v1/orders/2
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@Valid @PathVariable("id") Long orderId) {
        try {
            Order existingOrder = orderService.getOrder(orderId);
            OrderResponse orderResponse = OrderResponse.fromOrder(existingOrder);
            return ResponseEntity.ok(orderResponse);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PutMapping("/{id}")
    //PUT http://localhost:8088/api/v1/orders/2
    //công việc của admin
    public ResponseEntity<?> updateOrder(
            @Valid @PathVariable long id,
            @Valid @RequestBody OrderDTO orderDTO,
            @RequestHeader(name = "Authorization") String authorization) {

        try {
            String token = AuthJwtToken.extractToken(authorization);
            Order order = orderService.updateOrder(id, orderDTO);
            logRabbit("Updated order ID: " + id);

            return ResponseEntity.ok()
                    .header(HttpHeaders.AUTHORIZATION,"BEAER "+token)
                    .body(order);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOrder(@Valid @PathVariable Long id) {
        //xóa mềm => cập nhật trường active = false
        orderService.deleteOrder(id);
        logRabbit("Deleted order ID: " + id);

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

    private void logRabbit(String msg) {
        try {
            messageProducer.sendNotificationMessage(msg);
        } catch (Exception e) {
            System.err.println("RabbitMQ failed: " + msg);
        }
    }

    @GetMapping("/latest")
    public ResponseEntity<?> getLatestOrder(
            @AuthenticationPrincipal User userDetails

    ) {


        //  Lấy đơn hàng mới nhất
        Order order = orderRepository
                .findTopByUserIdOrderByOrderDateDesc(userDetails.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy đơn hàng mới nhất cho user id=" + userDetails.getId()
                ));
        //Build response
        Map<String,Object> body = Map.of(
                "user", UserResponse.fromUser(userDetails),
                "order", OrderResponse.fromOrder(order)
        );

        return ResponseEntity.ok()
                .body(body);
    }




}
