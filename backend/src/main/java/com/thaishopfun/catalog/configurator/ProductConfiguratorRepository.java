package com.thaishopfun.catalog.configurator;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProductConfiguratorRepository extends JpaRepository<ProductConfigurator, UUID> {}
