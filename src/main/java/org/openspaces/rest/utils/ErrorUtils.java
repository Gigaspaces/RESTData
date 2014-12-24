package org.openspaces.rest.utils;

/**
 * Created by yohana on 12/23/14.
 */
public abstract class ErrorUtils {

    public static String toJSON(Exception e) {
            return String.format("{\"status\":\"error\", \"error\":{\"java.class\":\"%s\", \"message\":\"%s\"}}", e.getClass(), escapeJSON(e.getMessage()));
    }
    public static String toJSON(String message) {
        return String.format("{\"status\":\"error\", \"error\":{\"message\":\"%s\"}}", escapeJSON(message));
    }

    public static String escapeJSON(String str) {
        return str.replace('\n',' ').replace('\t',' ').replace('\r',' ');
    }
}
