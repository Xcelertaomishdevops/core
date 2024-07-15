package com.taomish.actualization.repo;

import com.taomish.actualization.model.GrnStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GrnStockRepo extends JpaRepository<GrnStock, UUID> {
    GrnStock findByTenantIdAndGrnId(String tenantId, String grnId);
    List<GrnStock> findAllByTenantIdAndActualizeIdAndGrnIdNot(String tenantId, String ActualizeId, String grnId);
}
