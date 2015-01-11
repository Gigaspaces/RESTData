package org.openspaces.rest.exceptions;

/**
 * Created by yohana on 12/31/14.
 */
public class TypeAlreadyRegisteredException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String typeName;


    public TypeAlreadyRegisteredException(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

}
