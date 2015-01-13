package org.openspaces.rest.utils;

/**
 * @author yohana
 * @since 10.1.0
 */
public class ExceptionMessage extends ErrorMessage {
    private String javaclass;

    public ExceptionMessage(Exception e) {
        super(e.getMessage());
        this.javaclass = e.getClass().toString();
    }

    public String getJavaclass() {
        return javaclass;
    }

    public void setJavaclass(String javaclass) {
        this.javaclass = javaclass;
    }
}
