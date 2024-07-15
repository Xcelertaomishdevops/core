package com.taomish.actualization.repo;

import com.taomish.actualization.model.ActualizedDocuments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "actualizationDocuments")
public interface ActualizationDocumentsRepo extends JpaRepository<ActualizedDocuments, UUID>, JpaSpecificationExecutor<ActualizedDocuments> {

    List<ActualizedDocuments> findByTenantIdAndAttachmentFileName(String tenantId,String attchementName);

    List<ActualizedDocuments> findAllByTenantIdAndPlannedObligationId(String tenantId, String plannedObligationId);
    List<ActualizedDocuments> findAllByTenantIdAndUuidIn(String tenantId, List<UUID> uuids);
    List<ActualizedDocuments> findAllByTenantIdAndPlannedObligationIdInOrderByUpdatedTimestampDesc(String tenantId, List<String> plannedObligationIds);
}
