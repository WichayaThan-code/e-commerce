package com.thaishopfun.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CarrierRepository extends JpaRepository<Carrier, String> {
    List<Carrier> findAllByOrderBySortOrderAscCodeAsc();

    /** Row lock on the carrier: serializes tracking-number assignment per carrier (uniqueness check + write). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Carrier c where c.code = :code")
    Optional<Carrier> lockByCode(@Param("code") String code);
}
