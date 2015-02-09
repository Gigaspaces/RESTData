/*
 * Copyright 2015 GigaSpaces Technologies Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at *
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License."
 */
package org.openspaces.rest.exceptions;

/**
 * This exception is used in cases when a client tries to perform an write/update operation
 * on a type that is not registered in space
 * @author Dan Kilman
 */
public class TypeNotFoundException
        extends RuntimeException
{
    private static final long serialVersionUID = 1L;
    private final String typeName;

    public TypeNotFoundException(String typeName) {
        this.typeName = typeName;
    }

    public String getTypeName() {
        return typeName;
    }

}
