package com.taomish.actualization.repo;
import com.taomish.actualization.model.ActualQuality;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;
@RepositoryRestResource(path = "actualQuality")
public interface ActualQualityRepo extends JpaRepository<ActualQuality, UUID>, JpaSpecificationExecutor<ActualQuality> {
    List<ActualQuality> findAllByPlannedObligationIdAndTenantId(String plannedObligationId, String tenantId);
    List<ActualQuality> findByTenantIdAndPlannedObligationIdAndActualizationId(String tenantId, String plannedObligationId, String actualizationId);
    List<ActualQuality> findAllByTenantIdAndActualizationId(String tenantId, String actualizationId);

    @Transactional
    @Modifying(flushAutomatically = true,clearAutomatically = true)
    void deleteAllByTenantIdAndPlannedObligationId(String tenantId,String plannedObligationId);
}
