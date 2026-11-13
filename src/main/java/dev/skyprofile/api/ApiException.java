package dev.skyprofile.api;

public class ApiException extends RuntimeException {
	public final int status;
	public final String code;
	public final long retryAfterSeconds;

	public ApiException(int status, String code, String detail, long retryAfterSeconds) {
		super(detail);
		this.status = status;
		this.code = code == null ? "" : code;
		this.retryAfterSeconds = retryAfterSeconds;
	}
}
