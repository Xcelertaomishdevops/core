package com.taomish.blmanagement.repository;

import com.taomish.blmanagement.model.BlRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "blrecord")
public interface BlRecordRepo extends JpaRepository<BlRecord, UUID> {

    List<BlRecord> findAllByTenantIdAndBlNumber(String tenantId, String blNumber);

    BlRecord findByTenantIdAndUuid(String tenantId, UUID uuid);

    @Transactional
    @Modifying
    List<BlRecord> deleteByTenantIdAndUuid(String tenantId, UUID uuid);

    BlRecord findByTenantIdAndPlannedObligationId(String tenantId, String plannedObligationId);
}
