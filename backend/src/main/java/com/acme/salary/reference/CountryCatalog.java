package com.acme.salary.reference;

import com.acme.salary.common.error.InvalidFieldException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * In-memory view of the (tiny, migration-managed) country table. Mapping thousands of employees
 * to currency/rate therefore never costs a join or an N+1 query.
 */
@Component
public class CountryCatalog {

    private final CountryRepository repository;
    private volatile Map<String, Country> byCode;

    public CountryCatalog(CountryRepository repository) {
        this.repository = repository;
    }

    public List<Country> all() {
        return List.copyOf(countries().values());
    }

    public Optional<Country> find(String code) {
        return Optional.ofNullable(code).map(countries()::get);
    }

    /** Resolves a country supplied in a request, reporting an unknown code as a field error. */
    public Country require(String field, String code) {
        return find(code).orElseThrow(() ->
                new InvalidFieldException(field, "UNKNOWN_COUNTRY", "Unsupported country: " + code));
    }

    private Map<String, Country> countries() {
        Map<String, Country> snapshot = byCode;
        if (snapshot == null) {
            Collection<Country> loaded = repository.findAll();
            snapshot = loaded.stream()
                    .sorted(Comparator.comparing(Country::getName))
                    .collect(Collectors.toMap(Country::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
            byCode = snapshot;
        }
        return snapshot;
    }
}
