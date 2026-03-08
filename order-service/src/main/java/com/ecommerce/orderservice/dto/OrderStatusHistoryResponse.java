package com.ecommerce.orderservice.dto;

import com.ecommerce.orderservice.model.OrderStatusHistory;

import java.time.LocalDateTime;

public record OrderStatusHistoryResponse(
        Long id,
        String fromStatus,
        String toStatus,
        String reason,
        LocalDateTime changedAt
) {
    public static OrderStatusHistoryResponse fromEntity(OrderStatusHistory history) {
        return new OrderStatusHistoryResponse(
                history.getId(),
                history.getFromStatus() != null ? history.getFromStatus().name() : null,
                history.getToStatus().name(),
                history.getReason(),
                history.getChangedAt()
        );
    }
}
