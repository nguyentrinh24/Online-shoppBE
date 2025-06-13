package com.project.shopapp.services.OrderDetail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.project.shopapp.dtos.Order.OrderDetailDTO;
import com.project.shopapp.exceptions.DataNotFoundException;
import com.project.shopapp.exceptions.InvalidParamException;
import com.project.shopapp.models.Order;
import com.project.shopapp.models.OrderDetail;
import com.project.shopapp.models.Product;
import com.project.shopapp.repositories.OrderDetailRepository;
import com.project.shopapp.repositories.OrderRepository;
import com.project.shopapp.repositories.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Service
public class OrderDetailService implements IOrderDetailService {
    private final OrderRepository orderRepository;
    private final OrderDetailRepository orderDetailRepository;
    private final ProductRepository productRepository;
    private final OrderDetailRedisService orderDetailRedisService;

    @Override
    @Transactional
    public OrderDetail createOrderDetail(OrderDetailDTO orderDetailDTO) throws Exception {

        Order order = orderRepository.findById(orderDetailDTO.getOrderId())
                .orElseThrow(() -> new DataNotFoundException(
                        "Cannot find Order with id : "+orderDetailDTO.getOrderId()));

        Product product = productRepository.findById(orderDetailDTO.getProductId())
                .orElseThrow(() -> new DataNotFoundException(
                        "Cannot find product with id: " + orderDetailDTO.getProductId()));


        if (orderDetailDTO.getNumberOfProducts() > product.getQuantity()) {
            throw new InvalidParamException(
                    String.format("Order quantity (%d) exceeds available quantity (%d) for product: %s",
                            orderDetailDTO.getNumberOfProducts(),
                            product.getQuantity(),
                            product.getName())
            );
        }


        product.setQuantity(product.getQuantity() - orderDetailDTO.getNumberOfProducts());
        product.setStock_quantity(product.getStock_quantity() - orderDetailDTO.getNumberOfProducts());
        productRepository.save(product);

        OrderDetail orderDetail = OrderDetail.builder()
                .order(order)
                .product(product)
                .numberOfProducts(orderDetailDTO.getNumberOfProducts())
                .price(orderDetailDTO.getPrice())
                .totalMoney(orderDetailDTO.getTotalMoney())
                .color(orderDetailDTO.getColor())
                .build();

        OrderDetail savedOrderDetail = orderDetailRepository.save(orderDetail);
        

        try {
            orderDetailRedisService.saveOrderDetailToCache(savedOrderDetail);
            orderDetailRedisService.clearOrderDetailsCache(order.getId());
        } catch (JsonProcessingException e) {
            // Log error but don't throw exception
            System.err.println("Error caching order detail: " + e.getMessage());
        }
        
        return savedOrderDetail;
    }

    @Override
    @Transactional
    public OrderDetail getOrderDetail(Long id) throws DataNotFoundException {
        try {

            OrderDetail cachedOrderDetail = orderDetailRedisService.getOrderDetailFromCache(id);
            if (cachedOrderDetail != null) {
                return cachedOrderDetail;
            }
        } catch (JsonProcessingException e) {

            System.err.println("Error getting order detail from cache: " + e.getMessage());
        }


        OrderDetail orderDetail = orderDetailRepository.findById(id)
                .orElseThrow(()->new DataNotFoundException("Cannot find OrderDetail with id: "+id));
        

        try {
            orderDetailRedisService.saveOrderDetailToCache(orderDetail);
        } catch (JsonProcessingException e) {

            System.err.println("Error caching order detail: " + e.getMessage());
        }
        
        return orderDetail;
    }

    @Override
    @Transactional
    public OrderDetail updateOrderDetail(Long id, OrderDetailDTO orderDetailDTO)
            throws DataNotFoundException, InvalidParamException {

        OrderDetail existingOrderDetail = orderDetailRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Cannot find order detail with id: "+id));
        Order existingOrder = orderRepository.findById(orderDetailDTO.getOrderId())
                .orElseThrow(() -> new DataNotFoundException("Cannot find order with id: "+id));
        Product existingProduct = productRepository.findById(orderDetailDTO.getProductId())
                .orElseThrow(() -> new DataNotFoundException(
                        "Cannot find product with id: " + orderDetailDTO.getProductId()));


        int availableQuantity = existingProduct.getQuantity() + existingOrderDetail.getNumberOfProducts();
        if (orderDetailDTO.getNumberOfProducts() > availableQuantity) {
            throw new InvalidParamException(
                    String.format("Order quantity (%d) exceeds available quantity (%d) for product: %s",
                            orderDetailDTO.getNumberOfProducts(),
                            availableQuantity,
                            existingProduct.getName())
            );
        }

        // Update product quantity and stock_quantity
        existingProduct.setQuantity(availableQuantity - orderDetailDTO.getNumberOfProducts());
        existingProduct.setStock_quantity(existingProduct.getStock_quantity() + 
            existingOrderDetail.getNumberOfProducts() - orderDetailDTO.getNumberOfProducts());
        productRepository.save(existingProduct);

        existingOrderDetail.setPrice(orderDetailDTO.getPrice());
        existingOrderDetail.setNumberOfProducts(orderDetailDTO.getNumberOfProducts());
        existingOrderDetail.setTotalMoney(orderDetailDTO.getTotalMoney());
        existingOrderDetail.setColor(orderDetailDTO.getColor());
        existingOrderDetail.setOrder(existingOrder);
        existingOrderDetail.setProduct(existingProduct);
        
        OrderDetail updatedOrderDetail = orderDetailRepository.save(existingOrderDetail);
        
        // Update cache
        try {
            orderDetailRedisService.saveOrderDetailToCache(updatedOrderDetail);
            orderDetailRedisService.clearOrderDetailsCache(existingOrder.getId());
        } catch (JsonProcessingException e) {
            // Log error but don't throw exception
            System.err.println("Error caching order detail: " + e.getMessage());
        }
        
        return updatedOrderDetail;
    }

    @Override
    @Transactional
    public void deleteById(Long id) throws DataNotFoundException {
        OrderDetail orderDetail = orderDetailRepository.findById(id)
                .orElseThrow(() -> new DataNotFoundException("Cannot find order detail with id: " + id));
        

        Product product = orderDetail.getProduct();
        product.setQuantity(product.getQuantity() + orderDetail.getNumberOfProducts());
        product.setStock_quantity(product.getStock_quantity() + orderDetail.getNumberOfProducts());
        productRepository.save(product);
        

        orderDetailRedisService.clearOrderDetailCache(id);
        orderDetailRedisService.clearOrderDetailsCache(orderDetail.getOrder().getId());
        
        orderDetailRepository.deleteById(id);
    }

    @Override
    @Transactional
    public List<OrderDetail> findByOrderId(Long orderId) {
        try {

            List<OrderDetail> cachedOrderDetails = orderDetailRedisService.getOrderDetailsFromCache(orderId);
            if (cachedOrderDetails != null) {
                return cachedOrderDetails;
            }
        } catch (JsonProcessingException e) {

            System.err.println("Error getting order details from cache: " + e.getMessage());
        }


        List<OrderDetail> orderDetails = orderDetailRepository.findByOrderId(orderId);
        
        // Cache the order details
        try {
            orderDetailRedisService.saveOrderDetailsToCache(orderId, orderDetails);
        } catch (JsonProcessingException e) {
            // Log error but don't throw exception
            System.err.println("Error caching order details: " + e.getMessage());
        }
        
        return orderDetails;
    }
}
