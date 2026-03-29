package ai.llmmonkey.audit;

import ai.llmmonkey.model.AuditLogEntity;
import ai.llmmonkey.repository.AuditLogRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void logAction(String action, String tableName, String objectId,
                          Object beforeValue, Object afterValue,
                          String changedBy, String changedByKey) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setAction(action);
        entity.setTableName(tableName);
        entity.setObjectId(objectId);
        entity.setBeforeValue(serialize(beforeValue));
        entity.setUpdatedValues(serialize(afterValue));
        entity.setChangedBy(changedBy);
        entity.setChangedByKey(changedByKey);
        entity.setTimestamp(Instant.now());

        auditLogRepository.save(entity);
    }

    private String serialize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit value: {}", e.getMessage());
            return value.toString();
        }
    }
}
