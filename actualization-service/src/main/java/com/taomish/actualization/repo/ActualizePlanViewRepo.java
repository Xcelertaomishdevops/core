package com.taomish.actualization.repo;

import com.taomish.actualization.model.PlanViewActualization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;
import java.util.UUID;

@RestResource
public interface ActualizePlanViewRepo extends JpaRepository<PlanViewActualization, UUID>, JpaSpecificationExecutor<PlanViewActualization> {

    Page<PlanViewActualization> findAllByTenantId(String tenantId, Pageable pageable);
    PlanViewActualization findByTenantIdAndPlanId(String tenantId, String planId);
}
