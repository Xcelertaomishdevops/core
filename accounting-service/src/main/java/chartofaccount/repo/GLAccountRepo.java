package chartofaccount.repo;

import chartofaccount.model.GLAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.List;
import java.util.UUID;

@RepositoryRestResource(path = "gl_account")
public interface GLAccountRepo extends JpaRepository<GLAccount, UUID> {

    GLAccount findByTenantIdAndUuid(String tenantId, UUID uuid);

    List<GLAccount> findAllByTenantIdEqualsOrderByCreatedTimestampDesc(String tenantId);

    List<GLAccount> findAllByTenantIdAndGlAccount(String tenantId, String glAccount);
}
