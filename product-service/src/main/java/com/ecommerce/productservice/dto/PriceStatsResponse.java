package com.ecommerce.productservice.dto;

import java.math.BigDecimal;

// Record for price aggregation statistics.
// All computation happens in PostgreSQL — Java only receives these final numbers.
// With millions of products, the DB computes AVG/MIN/MAX in one pass,
// instead of loading all products into Java memory.

public record PriceStatsResponse(
        BigDecimal averagePrice,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        long productCount
) {}
