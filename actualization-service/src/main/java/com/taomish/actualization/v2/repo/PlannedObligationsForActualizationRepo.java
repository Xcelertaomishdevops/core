package com.taomish.actualization.v2.repo;

import com.taomish.actualization.v2.models.PlannedObligationsForActualization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PlannedObligationsForActualizationRepo extends JpaRepository<PlannedObligationsForActualization, UUID>, JpaSpecificationExecutor<PlannedObligationsForActualization> {
}
