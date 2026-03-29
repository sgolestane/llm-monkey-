package ai.llmmonkey.admin;

import ai.llmmonkey.admin.dto.CreateTeamRequest;
import ai.llmmonkey.model.TeamEntity;
import ai.llmmonkey.repository.TeamRepository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/team")
public class TeamManagementController {

    private final TeamRepository teamRepository;
    private final ObjectMapper objectMapper;

    public TeamManagementController(TeamRepository teamRepository, ObjectMapper objectMapper) {
        this.teamRepository = teamRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/new")
    public ResponseEntity<TeamEntity> createTeam(@RequestBody CreateTeamRequest request) {
        TeamEntity team = new TeamEntity();
        team.setTeamAlias(request.teamAlias());
        team.setOrganizationId(request.organizationId());
        team.setModels(joinList(request.models()));
        team.setMaxBudget(request.maxBudget());
        team.setTpmLimit(request.tpmLimit());
        team.setRpmLimit(request.rpmLimit());
        team.setMetadata(serializeMap(request.metadata()));
        TeamEntity saved = teamRepository.save(team);
        return ResponseEntity.ok(saved);
    }

    @GetMapping("/list")
    public ResponseEntity<List<TeamEntity>> listTeams() {
        return ResponseEntity.ok(teamRepository.findAll());
    }

    @GetMapping("/info/{teamId}")
    public ResponseEntity<TeamEntity> getTeamInfo(@PathVariable UUID teamId) {
        return teamRepository.findById(teamId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/update/{teamId}")
    public ResponseEntity<TeamEntity> updateTeam(@PathVariable UUID teamId,
                                                  @RequestBody CreateTeamRequest request) {
        return teamRepository.findById(teamId)
                .map(team -> {
                    if (request.teamAlias() != null) team.setTeamAlias(request.teamAlias());
                    if (request.models() != null) team.setModels(joinList(request.models()));
                    if (request.maxBudget() != null) team.setMaxBudget(request.maxBudget());
                    if (request.tpmLimit() != null) team.setTpmLimit(request.tpmLimit());
                    if (request.rpmLimit() != null) team.setRpmLimit(request.rpmLimit());
                    if (request.metadata() != null) team.setMetadata(serializeMap(request.metadata()));
                    return ResponseEntity.ok(teamRepository.save(team));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/delete/{teamId}")
    public ResponseEntity<Void> deleteTeam(@PathVariable UUID teamId) {
        teamRepository.deleteById(teamId);
        return ResponseEntity.ok().build();
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
