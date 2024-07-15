package com.taomish.actualization.v2.repo;

import com.taomish.actualization.v2.models.PlannedObligationsForDocByPass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PlannedObligationForDocByPassRepo extends JpaRepository<PlannedObligationsForDocByPass,UUID>, JpaSpecificationExecutor<PlannedObligationsForDocByPass> {

}
