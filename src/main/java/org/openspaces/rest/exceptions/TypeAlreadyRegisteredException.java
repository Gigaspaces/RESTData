package org.openspaces.rest.exceptions;

/**
 * @author yohana
 * @since 10.1.0
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
