package com.project.shopapp.services.OrderDetail;

import com.project.shopapp.dtos.Order.OrderDetailDTO;
import com.project.shopapp.exceptions.DataNotFoundException;
import com.project.shopapp.exceptions.InvalidParamException;
import com.project.shopapp.models.OrderDetail;

import java.util.List;

public interface IOrderDetailService {
    OrderDetail createOrderDetail(OrderDetailDTO orderDetailDTO) throws Exception;
    OrderDetail getOrderDetail(Long id) throws DataNotFoundException;
    OrderDetail updateOrderDetail(Long id, OrderDetailDTO orderDetailDTO) throws DataNotFoundException, InvalidParamException;
    void deleteById(Long id) throws DataNotFoundException;
    List<OrderDetail> findByOrderId(Long orderId);
}
