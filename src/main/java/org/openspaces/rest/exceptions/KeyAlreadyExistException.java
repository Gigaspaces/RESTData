package org.openspaces.rest.exceptions;

/**
 * Created by yohana on 12/31/14.
 */
public class KeyAlreadyExistException extends Exception {
    private String key;
    public KeyAlreadyExistException(String key) {
        this.key = key;
    }
}
