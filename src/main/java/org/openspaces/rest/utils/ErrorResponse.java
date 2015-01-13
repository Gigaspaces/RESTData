package org.openspaces.rest.utils;

/**
 * @author yohana
 * @since 10.1.0
 */
public class ErrorResponse {
    private String status;
    private ErrorMessage error;

    public ErrorResponse(ErrorMessage error) {
        status = "error";
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public ErrorMessage getError() {
        return error;
    }

    public void setError(ErrorMessage error) {
        this.error = error;
    }
}
