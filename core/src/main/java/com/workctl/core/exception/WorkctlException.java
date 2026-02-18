package com.workctl.core.exception;

public class WorkctlException extends Exception {
    public WorkctlException(String message) {
        super(message);
    }

    public WorkctlException(String message, Throwable cause) {
        super(message, cause);
    }
}
