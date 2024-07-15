package com.taomish.actualization.repo;

import com.taomish.actualization.model.BillOfLanding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "billOfLanding")
public interface BillOfLandingRepo extends JpaRepository<BillOfLanding, UUID> {
    BillOfLanding findByPlannedObligationIdAndSplitSequenceNumberAndTenantId(String plannedObligationId,double splitSequenceNumber,String tenantId);
    List<BillOfLanding> findAllByPlannedObligationIdAndSplitSequenceNumberAndTenantIdAndSurrenderedOrderByCreatedTimestampDesc(String plannedObligationId,double splitSequenceNumber,String tenantId,Boolean surrendered);
    List<BillOfLanding> findAllByActualizationIdAndSplitSequenceNumberAndTenantIdAndSurrendered(String actualizationId,double splitSequenceNumber,String tenantId,Boolean surrenderd);
    BillOfLanding findByPlannedObligationIdAndBlNumberAndTenantId(String plannedObligationId,String blnumber,String tenantId);
    BillOfLanding findByPlannedObligationIdAndBlNumberAndTenantIdAndSurrendered(String plannedObligationId,String blnumber,String tenantId,Boolean surrendered);
    List<BillOfLanding> findAllByPlannedObligationIdAndTenantIdOrderBySplitSequenceNumberAsc(String plannedObligationId,String tenantId);

    List<BillOfLanding> findAllByTenantIdAndPlannedObligationId(String tenantId, String plannedObligationId);
    List<BillOfLanding> findAllByTenantIdAndPlannedObligationIdAndSplitSequenceNumber(String tenantId, String plannedObligationId,double splitSequenceNumber);
    BillOfLanding findByPlannedObligationIdAndTenantId(String plannedObligationId,String tenantId);
}
