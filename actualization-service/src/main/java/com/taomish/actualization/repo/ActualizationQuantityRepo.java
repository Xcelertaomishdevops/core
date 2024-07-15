package com.taomish.actualization.repo;

import com.taomish.actualization.model.ActualizedQuantityObligations;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "actualizationQuantity")
public interface ActualizationQuantityRepo extends JpaRepository<ActualizedQuantityObligations, UUID>, JpaSpecificationExecutor<ActualizedQuantityObligations> {

    public List<ActualizedQuantityObligations> findAllByTenantIdAndPlannedObligationId(String tenantId,String plannedObligationId);
    public List<ActualizedQuantityObligations> findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(String tenantId,String plannedObligationId,int splitSequenceNumber);
    ActualizedQuantityObligations findAllByTenantIdAndPlannedObligationIdAndActualizationId(String tenantId,String plannedObligationId,String actualizationId);
    List<ActualizedQuantityObligations> findAllByPlannedObligationIdAndTenantIdOrderBySplitSequenceNumberAsc(String plannedObligationId,String tenantId);
    ActualizedQuantityObligations findByPlannedObligationIdAndSplitSequenceNumberAndTenantIdOrderBySplitSequenceNumberAsc(String plannedObligationId,Integer splitSequenceNumber,String tenantId);
    ActualizedQuantityObligations findByTenantIdAndActualizationId(String tenantId, String actualizationId);
    ActualizedQuantityObligations findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(String plannedObligationId,Integer splitSequenceNumber,String tenantId);
}
