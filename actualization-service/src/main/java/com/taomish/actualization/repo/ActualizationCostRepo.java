package com.taomish.actualization.repo;

import com.taomish.actualization.model.ActualizedCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "actualizationCost")
public interface ActualizationCostRepo extends JpaRepository<ActualizedCost, UUID>, JpaSpecificationExecutor<ActualizedCost> {


    List<ActualizedCost> findAllByTenantIdAndEstimatedCostId(String tenantId,String chargeId);
    List<ActualizedCost> findAllByTenantIdAndPlannedObligationId(String tenantId,String plannedObligationId);

    List<ActualizedCost> findAllByTenantIdAndEstimatedCostIdAndPlannedObligationId(String tenantId,String chargeId,String plannedObligationID);

    List<ActualizedCost> findAllByTenantIdAndActualizedCostId(String tenantId,String actualizedCostId);

    ActualizedCost findByTenantIdAndTradeIdAndActualizedCostId(String tennatId,String tradeId,String actualizedCostId);

    ActualizedCost findByEstimatedCostIdAndTenantId(String costId,String tenantId);
    ActualizedCost findByEstimatedCostIdAndTenantIdAndSplitSequenceNumberAndPlannedObligationId(String costId,String tenantId,Integer splitNumber,String plannedObligationId);

}
