package com.taomish.actualization.repo;

import com.taomish.actualization.model.BillOfLanding;
import com.taomish.actualization.model.BillOfLandingV2;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BillOfLandingV2Repo extends JpaRepository<BillOfLandingV2, UUID> {
    List<BillOfLandingV2> findByPlanIdAndTenantId(String planId,String tenantId);
}
