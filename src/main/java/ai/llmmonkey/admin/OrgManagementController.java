package ai.llmmonkey.admin;

import ai.llmmonkey.admin.dto.CreateOrgRequest;
import ai.llmmonkey.model.OrganizationEntity;
import ai.llmmonkey.repository.OrganizationRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/organization")
public class OrgManagementController {

    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;

    public OrgManagementController(OrganizationRepository organizationRepository, ObjectMapper objectMapper) {
        this.organizationRepository = organizationRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/new")
    public ResponseEntity<OrganizationEntity> createOrg(@RequestBody CreateOrgRequest request) {
        OrganizationEntity org = new OrganizationEntity();
        org.setAlias(request.alias());
        org.setModels(joinList(request.models()));
        org.setMetadata(serializeMap(request.metadata()));
        OrganizationEntity saved = organizationRepository.save(org);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/list")
    public ResponseEntity<List<OrganizationEntity>> listOrgs() {
        return ResponseEntity.ok(organizationRepository.findAll());
    }

    @GetMapping("/info/{orgId}")
    public ResponseEntity<OrganizationEntity> getOrgInfo(@PathVariable UUID orgId) {
        return organizationRepository.findById(orgId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/update/{orgId}")
    public ResponseEntity<OrganizationEntity> updateOrg(@PathVariable UUID orgId,
                                                         @RequestBody CreateOrgRequest request) {
        return organizationRepository.findById(orgId)
                .map(org -> {
                    if (request.alias() != null) org.setAlias(request.alias());
                    if (request.models() != null) org.setModels(joinList(request.models()));
                    if (request.metadata() != null) org.setMetadata(serializeMap(request.metadata()));
                    return ResponseEntity.ok(organizationRepository.save(org));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private String joinList(List<String> list) {
        if (list == null) return null;
        return String.join(",", list);
    }

    private String serializeMap(Map<String, Object> map) {
        if (map == null) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return map.toString();
        }
    }
}
