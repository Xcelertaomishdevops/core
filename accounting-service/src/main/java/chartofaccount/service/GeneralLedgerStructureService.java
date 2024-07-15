package chartofaccount.service;

import chartofaccount.dto.GLAccountDTO;
import chartofaccount.dto.GLStructureDTO;
import chartofaccount.model.GLAccount;
import chartofaccount.model.GLStructure;
import chartofaccount.repo.GLAccountRepo;
import chartofaccount.repo.GLStructureRepo;
import com.taomish.dtos.ReturnStatus;
import com.taomish.common.utils.TransactionIdUtil;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class GeneralLedgerStructureService {

    private static final Logger logger = LoggerFactory.getLogger(GeneralLedgerStructureService.class);
    @Autowired
    GLStructureRepo glStructureRepo;
    @Autowired
    GLAccountRepo glAccountRepo;

    /**
     * Get All Structure
     *
     * @param tenantId
     * @return
     */
    public List<GLStructureDTO> getallglstructure(String tenantId) {
        logger.info("Entered into GeneralLedgerStructureService.getallglstructure() for tenantId {}: ",tenantId);
        List<GLStructureDTO> glStructureDTOList = null;
        try {
            glStructureDTOList = TransactionIdUtil.convertList(glStructureRepo.findAllByTenantIdEqualsOrderByCreatedTimestampDesc(tenantId), GLStructureDTO.class);
        } catch (Exception e) {
            logger.error("Failed to get all the GL Structure for tenantId {}: ",tenantId ,e);
        }
        logger.info("Exiting from GeneralLedgerStructureService.getallglstructure() for tenantId{} : ",tenantId);
        return glStructureDTOList;
    }

    /**
     * Save GL Structure
     *
     * @param glStructureDTO
     * @return
     */
    public ResponseEntity<?> saveglstructure(GLStructureDTO glStructureDTO) {
        logger.info("Entered GeneralLedgerStructureService.saveglstructure()");
        ResponseEntity response = null;
        List<GLStructure> glStructureList = TransactionIdUtil.convertList(glStructureRepo.findAllByTenantIdAndGlStructure
                (glStructureDTO.getTenantId(), glStructureDTO.getGlStructure()), GLStructure.class);
        if (!glStructureList.isEmpty()) {
            response = new ResponseEntity(new ReturnStatus("Given Name already exists"), HttpStatus.BAD_REQUEST);
            return response;
        }
        GLStructure glStructure = new GLStructure();
        BeanUtils.copyProperties(glStructureDTO, glStructure);
        response = new ResponseEntity(glStructureRepo.save(glStructure), HttpStatus.OK);
        logger.info("Exit GeneralLedgerStructureService.saveglstructure()");
        return response;
    }

    /**
     * Update Gl Structure
     *
     * @param glStructureDTO
     * @param tenantId
     * @return
     */
    public ResponseEntity updateglstructure(GLStructureDTO glStructureDTO, String tenantId) {
        logger.info("Entered to  update GeneralLedgerStructureService for glStructure {} :" , glStructureDTO.getUuid());
        ResponseEntity responseEntity = null;
        try {
            GLStructure glStructure = glStructureRepo.findByTenantIdAndUuid(tenantId, glStructureDTO.getUuid());
            if (glStructure == null) {
                responseEntity = new ResponseEntity(new ReturnStatus("No Gl Structure found for the given tenantId and Uuid"), HttpStatus.NOT_FOUND);
                logger.error("Gl Structure is empty");
                return responseEntity;
            } else {
                BeanUtils.copyProperties(glStructureDTO, glStructure, "uuid");
                glStructure.setTenantId(tenantId);
                glStructureRepo.save(glStructure);
                logger.info("updated Gl Struture for Gl Structure Uuid :{} " ,glStructureDTO.getUuid());
            }
            responseEntity = new ResponseEntity(new ReturnStatus("Successfully updated Gl Structure(s)"), HttpStatus.OK);
        } catch (Exception e) {
            logger.error("GL Struture update failed",e);
            responseEntity = new ResponseEntity("Gl Structure update Failed,Because of the following message: " , HttpStatus.INTERNAL_SERVER_ERROR);
        }
        logger.info("Exit GeneralLedgerStructureService.updateglstructure()");
        return responseEntity;
    }

    /**
     * Get GL Structure By UUID
     *
     * @param tenantId
     * @param uuid
     * @return
     */
    public GLStructureDTO getglstructurebyuuid(String tenantId, String uuid) {
        logger.info("Entered into GeneralLedgerStructureService.getglstructurebyuuid()");
        GLStructureDTO glStructureDTO = new GLStructureDTO();
        try {
            GLStructure glStructure = glStructureRepo.findByTenantIdAndUuid(tenantId, UUID.fromString(uuid));
            BeanUtils.copyProperties(glStructure, glStructureDTO);
        } catch (Exception e) {
            logger.error("Failed to get the GL Structure for tenantId  : " ,e);
        }
        logger.info("Exiting from GeneralLedgerStructureService.getglstructurebyuuid() ");
        return glStructureDTO;
    }

    /**
     * Import GL Structure
     *
     * @param glStructureDTOList
     * @return
     */
    public Object importglstructure(List<GLStructureDTO> glStructureDTOList) {
        logger.info("Entered into GeneralLedgerStructureService.importglstructure");
        ModelMapper modelMapper = new ModelMapper();
        ResponseEntity response = null;
        try {
            List<GLStructure> glStructureList = glStructureDTOList.stream()
                    .map(entity -> modelMapper.map(entity, GLStructure.class)).toList();
            glStructureRepo.saveAll(glStructureList);
            return ResponseEntity.ok(glStructureRepo.saveAll(glStructureList));
        } catch (Exception e) {
            response = new ResponseEntity("import API failed :", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("import API failed :",e);
        }
        logger.info("Exited from GeneralLedgerStructureService.importglstructure");
        return response;
    }

    /**
     * Get Parent List
     *
     * @param tenantId
     * @param glStructure
     * @return
     */
    public ResponseEntity getParentList(String tenantId, String glStructure) {
        logger.info("Entered into GeneralLedgerStructureService.getParentList()");
        ResponseEntity response = null;
        try {
            response = new ResponseEntity(TransactionIdUtil.convertList(glStructureRepo.findByTenantIdAndGlStructureNotAndParentStructureNot(tenantId, glStructure, glStructure), GLStructureDTO.class), HttpStatus.OK);
        } catch (Exception ex) {
            response = new ResponseEntity("Couldn't fetch the Gl Structure details, please contact admin", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("Failed to fetch the Gl Structure details",ex);
        }
        logger.info("Exited from GeneralLedgerStructureService.getParentList()");
        return response;
    }

    /**
     * Get All GL Account
     *
     * @param tenantId
     * @return
     */
    public List<GLAccountDTO> getallglaccount(String tenantId) {
        logger.info("Entered into GeneralLedgerStructureService.getallglaccount() for tenantId {}: " , tenantId);
        List<GLAccountDTO> glAccountDTOList = null;
        try {
            glAccountDTOList = TransactionIdUtil.convertList(glAccountRepo.findAllByTenantIdEqualsOrderByCreatedTimestampDesc(tenantId), GLAccountDTO.class);
        } catch (Exception e) {
            logger.error("Failed to get all the GL Account for tenantId {}: " ,tenantId,e);
        }
        logger.info("Exiting from GeneralLedgerStructureService.getallglaccount() for tenantId {} : ",tenantId);
        return glAccountDTOList;
    }

    /**
     * Save GL Account
     *
     * @param glAccountDTO
     * @return
     */
    public ResponseEntity<?> saveglaccount(GLAccountDTO glAccountDTO) {
        logger.info("Entered GeneralLedgerStructureService.saveglaccount()");
        ResponseEntity response = null;
        List<GLAccount> glAccountList = TransactionIdUtil.convertList(glAccountRepo.findAllByTenantIdAndGlAccount
                (glAccountDTO.getTenantId(), glAccountDTO.getGlAccount()), GLAccount.class);
        if (!glAccountList.isEmpty()) {
            response = new ResponseEntity(new ReturnStatus("Given Name already exists"), HttpStatus.BAD_REQUEST);
            return response;
        }
        GLAccount glAccount = new GLAccount();
        BeanUtils.copyProperties(glAccountDTO, glAccount);
        response = new ResponseEntity(glAccountRepo.save(glAccount), HttpStatus.OK);
        logger.info("Exit GeneralLedgerStructureService.saveglaccount()");
        return response;
    }

    /**
     * Update GL Account
     *
     * @param glAccountDTO
     * @param tenantId
     * @return
     */
    public ResponseEntity updateglaccount(GLAccountDTO glAccountDTO, String tenantId) {
        logger.info("Entered to update GeneralLedgerStructureService for updateglaccount {} :", glAccountDTO.getUuid());
        ResponseEntity responseEntity = null;
        try {
            GLAccount glAccount = glAccountRepo.findByTenantIdAndUuid(tenantId, glAccountDTO.getUuid());
            if (glAccount == null) {
                responseEntity = new ResponseEntity(new ReturnStatus("No Gl Account found for the given tenantId and Uuid"), HttpStatus.NOT_FOUND);
                logger.error("Gl Account is empty");
                return responseEntity;
            } else {
                BeanUtils.copyProperties(glAccountDTO, glAccount, "uuid");
                glAccount.setTenantId(tenantId);
                glAccountRepo.save(glAccount);
                logger.info("updated Gl Account for Gl Account Uuid :{} ", glAccountDTO.getUuid());
            }
            responseEntity = new ResponseEntity("Successfully updated Gl Account(s)", HttpStatus.OK);
        } catch (Exception e) {
            logger.error("GL Account update failed",e);
            responseEntity = new ResponseEntity("Gl Account update Failed,Because of the following message: ", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        logger.info("Exit GeneralLedgerStructureService.updateglaccount()");
        return responseEntity;
    }

    /**
     * Get By UUID And TenantId
     *
     * @param tenantId
     * @param uuid
     * @return
     */
    public GLAccountDTO getglaccountbyuuid(String tenantId, String uuid) {
        logger.info("Entered into GeneralLedgerStructureService.getglaccountbyuuid()");
        GLAccountDTO glAccountDTO = new GLAccountDTO();
        try {
            GLAccount glAccount = glAccountRepo.findByTenantIdAndUuid(tenantId, UUID.fromString(uuid));
            BeanUtils.copyProperties(glAccount, glAccountDTO);
        } catch (Exception e) {
            logger.error("Failed to get the GL Account for tenantId : " ,e);
        }
        logger.info("Exiting from GeneralLedgerStructureService.getglaccountbyuuid()");
        return glAccountDTO;
    }

    /**
     * Import GL Account
     *
     * @param glAccountDTOList
     * @return
     */
    public Object importglaccount(List<GLAccountDTO> glAccountDTOList) {
        logger.info("Entered into GeneralLedgerStructureService.importglaccount");
        ModelMapper modelMapper = new ModelMapper();
        ResponseEntity response = null;
        try {
            List<GLAccount> glAccountList = glAccountDTOList.stream()
                    .map(entity -> modelMapper.map(entity, GLAccount.class)).toList();
            glAccountRepo.saveAll(glAccountList);
            return ResponseEntity.ok(glAccountRepo.saveAll(glAccountList));
        } catch (Exception e) {
            response = new ResponseEntity("import API failed", HttpStatus.INTERNAL_SERVER_ERROR);
            logger.error("import API failed",e);
        }
        logger.info("Exited from GeneralLedgerStructureService.importglaccount");
        return response;
    }

}
