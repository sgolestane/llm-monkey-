package ai.llmmonkey.api.dto;

public record ErrorResponse(ErrorDetail error) {

    public record ErrorDetail(
            String message,
            String type,
            String param,
            String code
    ) {}

    public static ErrorResponse of(String message, String type, String code) {
        return new ErrorResponse(new ErrorDetail(message, type, null, code));
    }
}
