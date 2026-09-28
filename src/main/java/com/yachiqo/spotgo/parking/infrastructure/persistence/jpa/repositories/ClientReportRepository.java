package com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.parking.domain.model.aggregates.ClientReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClientReportRepository extends JpaRepository<ClientReport, String> {
    boolean existsByCode(String code);
}
