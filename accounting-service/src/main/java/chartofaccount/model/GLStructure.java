package chartofaccount.model;



import com.taomish.common.jpa.AbstractBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "xceler_accounting_gl_structure")
public class GLStructure extends AbstractBaseEntity {

    @Column
    private String glStructure;

    @Column
    private String structureDescription;

    @Column
    private String structureType;

    @Column
    private String parentStructure;

    @Column
    private Boolean status;

    public String getGlStructure() {
        return glStructure;
    }

    public void setGlStructure(String glStructure) {
        this.glStructure = glStructure;
    }

    public String getStructureDescription() {
        return structureDescription;
    }

    public void setStructureDescription(String structureDescription) {
        this.structureDescription = structureDescription;
    }

    public String getStructureType() {
        return structureType;
    }

    public void setStructureType(String structureType) {
        this.structureType = structureType;
    }

    public String getParentStructure() {
        return parentStructure;
    }

    public void setParentStructure(String parentStructure) {
        this.parentStructure = parentStructure;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }
}
