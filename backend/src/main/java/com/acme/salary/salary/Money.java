package com.acme.salary.salary;

import java.util.Objects;

/** A whole-unit amount in an ISO-4217 currency. */
public record Money(long amount, String currency) {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }
}
