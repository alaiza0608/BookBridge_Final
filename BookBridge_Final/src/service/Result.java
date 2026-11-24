package service;

import java.util.Optional;

public final class Result<T> {
    private final boolean success;
    private final T data;
    private final String message;

    private Result(boolean success, T data, String message) {
        this.success = success;
        this.data = data;
        this.message = message;
    }

    public static <T> Result<T> ok(T data, String message) { return new Result<>(true, data, message); }
    public static <T> Result<T> fail(String message) { return new Result<>(false, null, message); }
    public boolean isSuccess() { return success; }
    public Optional<T> getData() { return Optional.ofNullable(data); }
    public String getMessage() { return message; }
}