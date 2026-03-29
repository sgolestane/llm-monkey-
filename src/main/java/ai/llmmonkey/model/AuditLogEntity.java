package ai.llmmonkey.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "timestamp", columnDefinition = "TIMESTAMPTZ")
    private Instant timestamp;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "changed_by_key")
    private String changedByKey;

    @Column(name = "action")
    private String action;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "object_id")
    private String objectId;

    @Column(name = "before_value", columnDefinition = "jsonb")
    private String beforeValue;

    @Column(name = "updated_values", columnDefinition = "jsonb")
    private String updatedValues;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public String getChangedByKey() {
        return changedByKey;
    }

    public void setChangedByKey(String changedByKey) {
        this.changedByKey = changedByKey;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public void setBeforeValue(String beforeValue) {
        this.beforeValue = beforeValue;
    }

    public String getUpdatedValues() {
        return updatedValues;
    }

    public void setUpdatedValues(String updatedValues) {
        this.updatedValues = updatedValues;
    }
}
