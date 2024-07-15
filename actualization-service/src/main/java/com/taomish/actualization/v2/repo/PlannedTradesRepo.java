package com.taomish.actualization.v2.repo;

import com.taomish.actualization.v2.models.PlannedTrades;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface PlannedTradesRepo extends JpaRepository<PlannedTrades,UUID>, JpaSpecificationExecutor<PlannedTrades>{
}
