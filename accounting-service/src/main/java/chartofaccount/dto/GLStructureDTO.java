package chartofaccount.dto;


import com.taomish.common.jpa.AbstractBaseDto;

public class GLStructureDTO extends AbstractBaseDto {

    private String glStructure;

    private String structureDescription;

    private String structureType;

    private String parentStructure;

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
