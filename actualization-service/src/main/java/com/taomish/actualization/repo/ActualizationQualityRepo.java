package com.taomish.actualization.repo;

import com.taomish.actualization.model.ActualizedQuality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "actualizationQuality")
public interface ActualizationQualityRepo extends JpaRepository<ActualizedQuality, UUID>, JpaSpecificationExecutor<ActualizedQuality> {

    public List<ActualizedQuality> findAllByPlannedObligationIdAndTenantId(String plannedObligationId,String tenantId);

    public List<ActualizedQuality> findAllByActualizedQualityIdAndTenantId(String actualizedQualityId,String tenantId);

}
