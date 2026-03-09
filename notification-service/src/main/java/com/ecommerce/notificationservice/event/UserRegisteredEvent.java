package com.ecommerce.notificationservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Represents the event: "A new user just registered."
//
// Published by: user-service (after successful registration)
// Consumed by: notification-service (to send a welcome email/message)
//
// Why a separate event class instead of passing the User entity?
//   1. Loose coupling — notification-service doesn't need to know about User entity internals
//   2. Only the data the consumer NEEDS — no passwords, no roles, no internal fields
//   3. Schema evolution — the event can change independently of the User entity

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
}
