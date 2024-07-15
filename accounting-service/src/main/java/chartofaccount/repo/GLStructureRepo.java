package chartofaccount.repo;

import chartofaccount.model.GLStructure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "gl_structure")
public interface GLStructureRepo extends JpaRepository<GLStructure, UUID> {

    GLStructure findByTenantIdAndUuid(String tenantId, UUID uuid);

    List<GLStructure> findAllByTenantIdAndGlStructure(String tenantId, String glStructure);

    List<GLStructure> findAllByTenantIdEqualsOrderByCreatedTimestampDesc(String tenantId);

    List<GLStructure> findByTenantIdAndGlStructureNotAndParentStructureNot(String tenantId, String glStructure, String parentStructure);






}
