package com.acme.salary.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** Read-only reference data, changed only through Flyway migrations. */
@Entity
@Immutable
@Table(name = "country")
public class Country {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "currency", nullable = false)
    private String currency;

    /** 1 unit of local currency expressed in USD. */
    @Column(name = "usd_rate", nullable = false)
    private double usdRate;

    protected Country() {
    }

    public Country(String code, String name, String currency, double usdRate) {
        this.code = code;
        this.name = name;
        this.currency = currency;
        this.usdRate = usdRate;
    }

    public long toUsd(long localAmount) {
        return Math.round(localAmount * usdRate);
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getCurrency() {
        return currency;
    }

    public double getUsdRate() {
        return usdRate;
    }
}
