package com.taomish.actualization.repo;
import com.taomish.actualization.model.ActualPackingDetails;
import com.taomish.actualization.model.ActualQuality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "actualPackingDetails")
public interface ActualPackingDetailsRepo extends JpaRepository<ActualPackingDetails, UUID>, JpaSpecificationExecutor<ActualPackingDetails> {
    List<ActualPackingDetails> findAllByPlannedObligationIdAndTenantId(String plannedObligationId, String tenantId);
    List<ActualPackingDetails>findAllByTenantIdAndActualizationId(String tenantId, String actualizationId);
}
