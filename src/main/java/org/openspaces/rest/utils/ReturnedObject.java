package org.openspaces.rest.utils;

import org.jsondoc.core.annotation.ApiObject;
import org.jsondoc.core.annotation.ApiObjectField;

/**
 * Created by yohana on 1/6/15.
 */
@ApiObject(name = "ReturnedObject")
public class ReturnedObject {
    @ApiObjectField(description = "The ID of the user")
    private String status;
    @ApiObjectField(description = "The ID of the user")
    private Object data;

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
