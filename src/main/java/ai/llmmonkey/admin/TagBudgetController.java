package ai.llmmonkey.admin;

import ai.llmmonkey.admin.dto.CreateTagBudgetRequest;
import ai.llmmonkey.admin.dto.TagSpendReport;
import ai.llmmonkey.model.SpendLogEntity;
import ai.llmmonkey.model.TagBudgetEntity;
import ai.llmmonkey.repository.SpendLogRepository;
import ai.llmmonkey.repository.TagBudgetRepository;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tag")
public class TagBudgetController {

    private final TagBudgetRepository tagBudgetRepository;
    private final SpendLogRepository spendLogRepository;

    public TagBudgetController(TagBudgetRepository tagBudgetRepository,
                               SpendLogRepository spendLogRepository) {
        this.tagBudgetRepository = tagBudgetRepository;
        this.spendLogRepository = spendLogRepository;
    }

    @PostMapping("/budget/new")
    public TagBudgetEntity createTagBudget(@RequestBody CreateTagBudgetRequest request) {
        TagBudgetEntity entity = new TagBudgetEntity();
        entity.setTag(request.tag());
        entity.setMaxBudget(request.maxBudget());
        entity.setSoftBudget(request.softBudget());
        entity.setTpmLimit(request.tpmLimit());
        entity.setRpmLimit(request.rpmLimit());
        entity.setBudgetDuration(request.budgetDuration());
        return tagBudgetRepository.save(entity);
    }

    @PostMapping("/budget/update")
    public TagBudgetEntity updateTagBudget(@RequestBody CreateTagBudgetRequest request) {
        TagBudgetEntity entity = tagBudgetRepository.findByTag(request.tag())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Tag budget not found: " + request.tag()));
        if (request.maxBudget() != null) entity.setMaxBudget(request.maxBudget());
        if (request.softBudget() != null) entity.setSoftBudget(request.softBudget());
        if (request.tpmLimit() != null) entity.setTpmLimit(request.tpmLimit());
        if (request.rpmLimit() != null) entity.setRpmLimit(request.rpmLimit());
        if (request.budgetDuration() != null) entity.setBudgetDuration(request.budgetDuration());
        return tagBudgetRepository.save(entity);
    }

    @PostMapping("/budget/delete")
    public Map<String, String> deleteTagBudget(@RequestBody Map<String, String> request) {
        String tag = request.get("tag");
        tagBudgetRepository.findByTag(tag).ifPresent(tagBudgetRepository::delete);
        return Map.of("status", "deleted", "tag", tag);
    }

    @GetMapping("/budget/info")
    public TagBudgetEntity getTagBudget(@RequestParam String tag) {
        return tagBudgetRepository.findByTag(tag)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Tag budget not found: " + tag));
    }

    @GetMapping("/budget/list")
    public List<TagBudgetEntity> listTagBudgets() {
        return tagBudgetRepository.findAll();
    }

    @GetMapping("/spend")
    public TagSpendReport getTagSpend(
            @RequestParam String tag,
            @RequestParam(required = false) String period) {

        Instant since = resolveSince(period);
        BigDecimal totalSpend;
        List<SpendLogEntity> logs;
        int totalTokens;

        if (since != null) {
            logs = spendLogRepository.findByTagAndCreatedAtAfter(tag, since);
            totalSpend = spendLogRepository.sumSpendByTagAndCreatedAtAfter(tag, since);
        } else {
            logs = spendLogRepository.findByTag(tag);
            totalSpend = spendLogRepository.sumSpendByTag(tag);
        }

        totalTokens = logs.stream()
                .mapToInt(l -> l.getPromptTokens() + l.getCompletionTokens())
                .sum();

        Map<String, BigDecimal> spendByModel = logs.stream()
                .collect(Collectors.groupingBy(
                        l -> l.getModel() != null ? l.getModel() : "unknown",
                        Collectors.reducing(BigDecimal.ZERO,
                                l -> l.getSpend() != null ? l.getSpend() : BigDecimal.ZERO,
                                BigDecimal::add)));

        return new TagSpendReport(
                tag,
                totalSpend != null ? totalSpend : BigDecimal.ZERO,
                logs.size(),
                totalTokens,
                spendByModel,
                period
        );
    }

    private Instant resolveSince(String period) {
        if (period == null || period.isBlank()) return null;
        Instant now = Instant.now();
        return switch (period.toLowerCase()) {
            case "1h" -> now.minus(1, ChronoUnit.HOURS);
            case "24h", "1d" -> now.minus(1, ChronoUnit.DAYS);
            case "7d" -> now.minus(7, ChronoUnit.DAYS);
            case "30d" -> now.minus(30, ChronoUnit.DAYS);
            default -> null;
        };
    }
}
