package org.openspaces.rest.exceptions;

/**
 * @author yohana
 * @since 10.1.0
 */
public class KeyAlreadyExistException extends Exception {
    private String key;
    public KeyAlreadyExistException(String key) {
        this.key = key;
    }
}
