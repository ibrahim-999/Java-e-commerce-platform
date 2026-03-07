package com.ecommerce.userservice.model;

// Enums are perfect for a fixed set of values.
// Using an enum instead of a String means:
//   - The compiler catches typos (UserStatus.ACTVE won't compile)
//   - You can't accidentally save "active" vs "Active" vs "ACTIVE"
//   - IDE autocomplete shows you all valid options

public enum UserStatus {
    ACTIVE,     // user can log in and use the platform
    INACTIVE,   // user has been deactivated by admin
    SUSPENDED   // user violated terms, temporarily banned
}
