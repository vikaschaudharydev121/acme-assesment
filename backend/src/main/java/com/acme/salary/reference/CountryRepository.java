package com.acme.salary.reference;

import org.springframework.data.jpa.repository.JpaRepository;

interface CountryRepository extends JpaRepository<Country, String> {
}
