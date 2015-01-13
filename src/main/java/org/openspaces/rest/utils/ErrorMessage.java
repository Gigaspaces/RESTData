package org.openspaces.rest.utils;

/**
 * @author yohana
 * @since 10.1.0
 */
public class ErrorMessage {
    protected String message;

    public ErrorMessage(String message) {
        this.message = escapeJSON(message);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = escapeJSON(message);
    }

    protected static String escapeJSON(String str) {
        return str.replace('\n',' ').replace('\t',' ').replace('\r',' ').replace('\"','\'');
    }
}
