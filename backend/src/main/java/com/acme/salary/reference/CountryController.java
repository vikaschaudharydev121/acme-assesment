package com.acme.salary.reference;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reference")
class CountryController {

    record CountryResponse(String code, String name, String currency, double usdRate) {
    }

    private final CountryCatalog catalog;

    CountryController(CountryCatalog catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/countries")
    List<CountryResponse> countries() {
        return catalog.all().stream()
                .map(c -> new CountryResponse(c.getCode(), c.getName(), c.getCurrency(), c.getUsdRate()))
                .toList();
    }
}
