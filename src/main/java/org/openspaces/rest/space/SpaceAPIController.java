/*
 * Copyright 2011 GigaSpaces Technologies Ltd
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
package org.openspaces.rest.space;


import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gigaspaces.annotation.pojo.FifoSupport;
import com.gigaspaces.client.WriteModifiers;
import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.*;
import com.gigaspaces.metadata.index.SpaceIndexType;
import com.gigaspaces.query.IdQuery;
import com.j_spaces.core.UnknownTypeException;
import com.j_spaces.core.client.SQLQuery;
import net.jini.core.lease.Lease;
import org.jsondoc.core.annotation.Api;
import org.jsondoc.core.annotation.ApiBodyObject;
import org.jsondoc.core.annotation.ApiMethod;
import org.jsondoc.core.annotation.ApiParam;
import org.jsondoc.core.pojo.ApiParamType;
import org.jsondoc.core.pojo.ApiVerb;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.space.CannotFindSpaceException;
import org.openspaces.rest.exceptions.*;
import org.openspaces.rest.utils.ControllerUtils;
import org.openspaces.rest.utils.ErrorUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring MVC controller for the RESTful Space API
 * <p/>
 * usage examples:
 * GET:
 * http://localhost:8080/rest/data/Item/_introduce_type?spaceid=customerid
 * <p/>
 * http://localhost:8080/rest/data/Item/1
 * http://192.168.9.47:8080/rest/data/Item/_criteria?q=data2='common'
 * <p/>
 * Limit result size:
 * http://192.168.9.47:8080/rest/data/Item/_criteria?q=data2='common'&s=10
 * <p/>
 * DELETE:
 * curl -XDELETE http://localhost:8080/rest/data/Item/1
 * curl -XDELETE http://localhost:8080/rest/data/Item/_criteria?q=id=1
 * <p/>
 * Limit result size:
 * curl -XDELETE http://localhost:8080/rest/data/Item/_criteria?q=data2='common'&s=5
 * <p/>
 * POST:
 * curl -XPOST -d '[{"id":"1", "data":"testdata", "data2":"common", "nestedData" : {"nestedKey1":"nestedValue1"}}, {"id":"2", "data":"testdata2", "data2":"common", "nestedData" : {"nestedKey2":"nestedValue2"}}, {"id":"3", "data":"testdata3", "data2":"common", "nestedData" : {"nestedKey3":"nestedValue3"}}]' http://localhost:8080/rest/data/Item
 * <p/>
 * <p/>
 * The response is a json object:
 * On Sucess:
 * { "status" : "success" }
 * If there is a data:
 * {
 * "status" : "success",
 * "data" : {...} or [{...}, {...}]
 * }
 * <p/>
 * On Failure:
 * If Inner error (TypeNotFound/ObjectNotFound) then
 * {
 * "status" : "error",
 * "error": {
 * "message": "some error message"
 * }
 * }
 * If it is a XAP exception:
 * {
 * "status" : "error",
 * "error": {
 * "java.class" : "the exception's class name",
 * "message": "exception.getMessage()"
 * }
 * }
 *
 * @author rafi
 * @since 8.0
 */
@Controller
@RequestMapping(value = "/*")
@Api(name = "Space API", description = "Methods for interacting with space")
public class SpaceAPIController {

    private static final String TYPE_DESCRIPTION = "The type name";

    @Value("${spaceName}")
    public void setSpaceName(String spaceName) {
        ControllerUtils.spaceName = spaceName;
    }

    @Value("${lookupGroups}")
    public void setLookupGroups(String lookupGroups) {
        ControllerUtils.lookupGroups = lookupGroups;
    }

    @Value("${lookupLocators}")
    public void setLookupLocators(String lookupLocators) {
        ControllerUtils.lookupLocators = lookupLocators;
    }


    private static final String QUERY_PARAM = "query";
    private static final String MAX_PARAM = "max";
    private static final String SPACEID_PARAM = "spaceid";

    private static int maxReturnValues = Integer.MAX_VALUE;
    private static final Logger logger = Logger.getLogger(SpaceAPIController.class.getName());

    private static Object emptyObject = new Object();

    /**
     * redirects to index view
     *
     * @return
     */
    /*@RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView redirectToIndex() {
        return new ModelAndView("index");
    }*/


    /**
     * REST GET for introducing type to space
     *
     * @param type type name
     * @return
     */
    @RequestMapping(value = "/{type}/_introduce_type", method = RequestMethod.GET)
    public
    @ResponseBody
    Map<String, Object> introduceType(
            @PathVariable String type,
            @RequestParam(value = SPACEID_PARAM, defaultValue = "id") String spaceID
    ) {
        if (logger.isLoggable(Level.FINE))
            logger.fine("introducing type: " + type);
        Map<String, Object> result = new Hashtable<String, Object>();
        try {
            GigaSpace gigaSpace = ControllerUtils.xapCache.get();
            SpaceTypeDescriptor typeDescriptor = gigaSpace.getTypeManager().getTypeDescriptor(type);
            if (typeDescriptor != null) {
                throw new TypeAlreadyRegisteredException(type);
            }

            SpaceTypeDescriptor spaceTypeDescriptor = new SpaceTypeDescriptorBuilder(type).idProperty(spaceID)
                    .routingProperty(spaceID).supportsDynamicProperties(true).create();
            gigaSpace.getTypeManager().registerTypeDescriptor(spaceTypeDescriptor);
            result.put("status", "success");
        } catch (IllegalStateException e) {
            result.put("status", "error");
            Map<String, Object> error = new HashMap<String, Object>();
            error.put("message", ErrorUtils.escapeJSON(e.getMessage()));
            result.put("error", error);
        }

        return result;
    }

    public String getString(JsonNode node) {
        return node == null ? null : node.asText();
    }

    public Boolean getBoolean(JsonNode node) {
        return node == null ? null : node.asBoolean();
    }

    @ApiMethod(
            path="/{type}/_introduce_type",
            verb= ApiVerb.PUT,
            description="Introduces the specified type to the space with the provided description in the body"
    )
    @RequestMapping(value = "/{type}/_introduce_type", method = RequestMethod.PUT)
    public
    @ResponseBody
    Map<String, Object> introduceTypeAdvanced(
            @PathVariable @ApiParam(name = "type", description = TYPE_DESCRIPTION, paramType = ApiParamType.PATH)String type,
            @RequestBody(required = false) @ApiBodyObject String requestBody
    ) {
        if (logger.isLoggable(Level.FINE))
            logger.fine("introducing type: " + type);

        if (requestBody == null) {
            throw new RestIntroduceTypeException("Request body cannot be empty");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            JsonNode actualObj = mapper.readTree(requestBody);

            //check that the json does not have any "unknown" elements
            Iterator<String> iterator = actualObj.fieldNames();
            while (iterator.hasNext()){
                String fieldName = iterator.next();
                if (!ControllerUtils.allowedFields.contains(fieldName)) {
                    throw new RestIntroduceTypeException("Unknown field: "+fieldName);
                }
            }

            SpaceTypeDescriptorBuilder spaceTypeDescriptor = new SpaceTypeDescriptorBuilder(type);

            JsonNode idProperty = actualObj.get("idProperty");
            if (idProperty == null) {
                throw new RestIntroduceTypeException("idProperty must be provided");
            } else {
                if (!idProperty.isObject()) {
                    throw new RestIntroduceTypeException("idProperty value must be object");
                }
                Iterator<String> idPropertyIterator = idProperty.fieldNames();
                while (idPropertyIterator.hasNext()) {
                    String fieldName = idPropertyIterator.next();
                    if (!fieldName.equals("propertyName")
                            && !fieldName.equals("autoGenerated")
                            && !fieldName.equals("indexType")) {
                        throw new RestIntroduceTypeException("Unknown idProperty field: " + fieldName);
                    }
                }
                String propertyName = getString(idProperty.get("propertyName"));
                if (propertyName != null && !idProperty.get("propertyName").isTextual()) {
                    throw new RestIntroduceTypeException("idProperty.propertyName must be textual");
                }
                Boolean autoGenerated = getBoolean(idProperty.get("autoGenerated"));
                if (autoGenerated != null && !idProperty.get("autoGenerated").isBoolean()) {
                    throw new RestIntroduceTypeException("idProperty.autoGenerated must be boolean");
                }
                String indexType = getString(idProperty.get("indexType"));
                if (indexType != null && !idProperty.get("indexType").isTextual()) {
                    throw new RestIntroduceTypeException("idProperty.indexType must be textual");
                }

                if (propertyName == null) {
                    throw new RestIntroduceTypeException("idProperty.propertyName must be provided");
                } else if (autoGenerated == null && indexType == null) {
                    spaceTypeDescriptor.idProperty(propertyName);
                } else if (autoGenerated != null && indexType == null) {
                    spaceTypeDescriptor.idProperty(propertyName, autoGenerated);
                } else if (autoGenerated != null && indexType != null) {
                    try {
                        spaceTypeDescriptor.idProperty(propertyName, autoGenerated, SpaceIndexType.valueOf(indexType));
                    } catch (IllegalArgumentException e) {
                        throw new RestIntroduceTypeException("Illegal idProperty.indexType: " + e.getMessage());
                    }
                } else {
                    throw new RestIntroduceTypeException("idProperty.indexType cannot be used without idProperty.autoGenerated");
                }
            }

            JsonNode routingProperty = actualObj.get("routingProperty");
            if (routingProperty != null) {
                if (!routingProperty.isObject()) {
                    throw new RestIntroduceTypeException("routingProperty value must be object");
                }
                Iterator<String> routingPropertyIterator = routingProperty.fieldNames();
                while (routingPropertyIterator.hasNext()) {
                    String fieldName = routingPropertyIterator.next();
                    if (!fieldName.equals("propertyName") && !fieldName.equals("indexType")) {
                        throw new RestIntroduceTypeException("Unknown routingProperty field: "+fieldName);
                    }
                }
                String propertyName = getString(routingProperty.get("propertyName"));
                if (propertyName != null && !routingProperty.get("propertyName").isTextual()) {
                    throw new RestIntroduceTypeException("routingProperty.propertyName must be textual");
                }
                String indexType = getString(routingProperty.get("indexType"));
                if (indexType != null && !routingProperty.get("indexType").isTextual()) {
                    throw new RestIntroduceTypeException("routingProperty.indexType must be textual");
                }

                if (propertyName == null) {
                    throw new RestIntroduceTypeException("routingProperty.propertyName must be provided");
                } else if (indexType == null) {
                    spaceTypeDescriptor.routingProperty(propertyName);
                } else { //(indexType != null)
                    try {
                        spaceTypeDescriptor.routingProperty(propertyName, SpaceIndexType.valueOf(indexType));
                    } catch (IllegalArgumentException e) {
                        throw new RestIntroduceTypeException("Illegal routingProperty.indexType: " + e.getMessage());
                    }
                }
            }

            JsonNode compoundIndex = actualObj.get("compoundIndex");
            if (compoundIndex != null) {
                Iterator<String> compoundIndexIterator = compoundIndex.fieldNames();
                while (compoundIndexIterator.hasNext()) {
                    String fieldName = compoundIndexIterator.next();
                    if (!fieldName.equals("paths") && !fieldName.equals("unique")) {
                        throw new RestIntroduceTypeException("Unknown compoundIndex field: "+fieldName);
                    }
                }
                JsonNode paths = compoundIndex.get("paths");
                if (paths != null && !paths.isArray()) {
                    throw new RestIntroduceTypeException("compoundIndex.paths must be array of strings");
                }
                Boolean unique = getBoolean(compoundIndex.get("unique"));
                if (unique != null && !compoundIndex.get("unique").isBoolean()){
                    throw new RestIntroduceTypeException("compoundIndex.unique must be boolean");
                }

                if (paths == null) {
                    throw new RestIntroduceTypeException("compoundIndex.paths must be provided");
                } else {
                    if (paths.size() == 0) {
                        throw new RestIntroduceTypeException("compoundIndex.paths cannot be empty");
                    }
                    String[] pathsArr = new String[paths.size()];
                    for (int i = 0; i < paths.size(); i++) {
                        pathsArr[i] = paths.get(i).asText();
                    }
                    if (unique == null) {
                        spaceTypeDescriptor.addCompoundIndex(pathsArr);
                    } else {
                        spaceTypeDescriptor.addCompoundIndex(pathsArr, unique);
                    }
                }
            }


            String fifoSupport = getString(actualObj.get("fifoSupport"));
            if (fifoSupport != null) {
                if (!actualObj.get("fifoSupport").isTextual()) {
                    throw new RestIntroduceTypeException("fifoSupport must be textual");
                }
                try {
                    spaceTypeDescriptor.fifoSupport(FifoSupport.valueOf(fifoSupport));
                } catch (IllegalArgumentException e) {
                    throw new RestIntroduceTypeException("Illegal fifoSupport: " + e.getMessage());
                }
            }


            Boolean blobStoreEnabled = getBoolean(actualObj.get("blobStoreEnabled"));
            if (blobStoreEnabled != null) {
                if (!actualObj.get("blobStoreEnabled").isBoolean()) {
                    throw new RestIntroduceTypeException("blobStoreEnabled must be boolean");
                }
                spaceTypeDescriptor.setBlobstoreEnabled(blobStoreEnabled);
            }


            String documentStorageType = getString(actualObj.get("storageType"));
            if (documentStorageType != null) {
                if (!actualObj.get("storageType").isTextual()) {
                    throw new RestIntroduceTypeException("storageType must be textual");
                }
                try {
                    spaceTypeDescriptor.storageType(StorageType.valueOf(documentStorageType));
                } catch (IllegalArgumentException e) {
                    throw new RestIntroduceTypeException("Illegal storageType: " + e.getMessage());
                }
            }


            Boolean supportsOptimisticLocking = getBoolean(actualObj.get("supportsOptimisticLocking"));
            if (supportsOptimisticLocking != null) {
                if (!actualObj.get("supportsOptimisticLocking").isBoolean()) {
                    throw new RestIntroduceTypeException("supportsOptimisticLocking must be boolean");
                }
                spaceTypeDescriptor.supportsOptimisticLocking(supportsOptimisticLocking);
            }


            Boolean supportsDynamicProperties = getBoolean(actualObj.get("supportsDynamicProperties"));
            if (supportsDynamicProperties != null) {
                if (!actualObj.get("supportsDynamicProperties").isBoolean()) {
                    throw new RestIntroduceTypeException("supportsDynamicProperties must be boolean");
                }
                spaceTypeDescriptor.supportsDynamicProperties(supportsDynamicProperties);
            } else {
                spaceTypeDescriptor.supportsDynamicProperties(true);
            }


            HashSet<String> fixedPropertiesNames = new HashSet<String>();
            JsonNode fixedProperties = actualObj.get("fixedProperties");
            if (fixedProperties != null) {
                for (int i = 0; i < fixedProperties.size(); i++) {
                    JsonNode fixedProperty = fixedProperties.get(i);
                    Iterator<String> fixedPropertyIterator = fixedProperties.fieldNames();
                    while (fixedPropertyIterator.hasNext()) {
                        String fieldName = fixedPropertyIterator.next();
                        if (!fieldName.equals("propertyName") && !fieldName.equals("propertyType")
                                && !fieldName.equals("documentSupport") && !fieldName.equals("storageType")) {
                            throw new RestIntroduceTypeException("Unknown field: "+fieldName +" for FixedProperty at index ["+i+"]");
                        }
                    }

                    String propertyName = getString(fixedProperty.get("propertyName"));
                    if (propertyName != null && !fixedProperty.get("propertyName").isTextual()) {
                        throw new RestIntroduceTypeException("propertyName of FixedProperty at index ["+i+"] must be textual");
                    }
                    String propertyType = getString(fixedProperty.get("propertyType"));
                    if (propertyType != null && !fixedProperty.get("propertyType").isTextual()) {
                        throw new RestIntroduceTypeException("propertyType of FixedProperty at index ["+i+"] must be textual");
                    }
                    String documentSupport = getString(fixedProperty.get("documentSupport"));
                    if (documentSupport != null && !fixedProperty.get("documentSupport").isTextual()) {
                        throw new RestIntroduceTypeException("documentSupport of FixedProperty at index ["+i+"] must be textual");
                    }
                    String propertyStorageType = getString(fixedProperty.get("storageType"));
                    if (propertyStorageType != null && !fixedProperty.get("storageType").isTextual()) {
                        throw new RestIntroduceTypeException("storageType of FixedProperty at index ["+i+"] must be textual");
                    }

                    if (propertyName == null) {
                        throw new RestIntroduceTypeException("Missing propertyName in FixedProperty at index [" + i + "]");
                    }
                    if (propertyType == null) {
                        throw new RestIntroduceTypeException("Missing propertyType in FixedProperty at index [" + i + "]");
                    }
                    if (fixedPropertiesNames.add(propertyName) == false) {
                        throw new KeyAlreadyExistException(propertyName);
                    }
                    Class propertyValueClass = ControllerUtils.javaPrimitives.get(propertyType);

                    if (documentSupport == null && propertyStorageType == null) {
                        if (propertyValueClass != null) {
                            spaceTypeDescriptor.addFixedProperty(propertyName, propertyValueClass);
                        } else {
                            spaceTypeDescriptor.addFixedProperty(propertyName, propertyType);
                        }
                    } else if (documentSupport == null && propertyStorageType != null) {
                        throw new RestIntroduceTypeException("Cannot apply storageType of FixedProperty without specifying documentSupport");
                    } else if (documentSupport != null && propertyStorageType == null) {
                        try {
                            if (propertyValueClass != null) {
                                spaceTypeDescriptor.addFixedProperty(propertyName, propertyValueClass, SpaceDocumentSupport.valueOf(documentSupport));
                            } else {
                                spaceTypeDescriptor.addFixedProperty(propertyName, propertyType, SpaceDocumentSupport.valueOf(documentSupport));
                            }
                        } catch (IllegalArgumentException e) {
                            throw new RestIntroduceTypeException("Illegal fixedProperty.documentSupport: " + e.getMessage());
                        }
                    } else {
                        SpaceDocumentSupport spaceDocumentSupport;
                        try {
                            spaceDocumentSupport = SpaceDocumentSupport.valueOf(documentSupport);
                        } catch (IllegalArgumentException e) {
                            throw new RestIntroduceTypeException("Illegal fixedProperty.documentSupport: " + e.getMessage());
                        }
                        StorageType storageType;
                        try {
                            storageType = StorageType.valueOf(propertyStorageType);
                        } catch (IllegalArgumentException e) {
                            throw new RestIntroduceTypeException("Illegal fixedProperty.storageType: " + e.getMessage());
                        }
                        if (propertyValueClass != null) {
                            spaceTypeDescriptor.addFixedProperty(propertyName, propertyValueClass, spaceDocumentSupport, storageType);
                        } else {
                            spaceTypeDescriptor.addFixedProperty(propertyName, propertyType, spaceDocumentSupport, storageType);
                        }
                    }

                }
            }

            GigaSpace gigaSpace = ControllerUtils.xapCache.get();
            SpaceTypeDescriptor typeDescriptor = gigaSpace.getTypeManager().getTypeDescriptor(type);
            if (typeDescriptor != null) {
                throw new TypeAlreadyRegisteredException(type);
            }

            gigaSpace.getTypeManager().registerTypeDescriptor(spaceTypeDescriptor.create());

            HashMap<String, Object> result = new HashMap<String, Object>();
            result.put("status", "success");
            return result;
        } catch (IOException e) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            result.put("status", "error");
            Map<String, Object> error = new HashMap<String, Object>();
            error.put("message", ErrorUtils.escapeJSON(e.getMessage()));
            result.put("error", error);
            return result;

        } catch (KeyAlreadyExistException e) {
            HashMap<String, Object> result = new HashMap<String, Object>();
            result.put("status", "error");
            Map<String, Object> error = new HashMap<String, Object>();
            error.put("message", ErrorUtils.escapeJSON(e.getMessage()));
            result.put("error", error);
            return result;
        }
    }

    /**
     * REST GET by query request handler
     *
     * @param type
     * @param query
     * @return
     * @throws ObjectNotFoundException
     */
    @ApiMethod(
            path="/{type}/",
            verb= ApiVerb.GET,
            description="Read multiple entries from space that matches the query."
    )
    @RequestMapping(value = "/{type}", method = RequestMethod.GET)
    public
    @ResponseBody
    Map<String, Object> getByQuery(
            @PathVariable @ApiParam(name = "type", description = TYPE_DESCRIPTION, paramType = ApiParamType.PATH) String type,
            @RequestParam(value = QUERY_PARAM, required = false)
            @ApiParam(name="query", description = "a SQLQuery that is a SQL-like syntax", paramType = ApiParamType.QUERY)String query,
            @RequestParam(value = MAX_PARAM, required = false)
            @ApiParam(name="size", clazz = Integer.class, description = "", paramType = ApiParamType.QUERY)Integer size) throws ObjectNotFoundException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("creating read query with type: " + type + " and query: " + query);

        if (query == null) {
            query = ""; //Query all the data
        }

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        SQLQuery<Object> sqlQuery = new SQLQuery<Object>(type, query);
        int maxSize = (size == null ? maxReturnValues : size.intValue());
        Object[] docs;
        try {
            docs = gigaSpace.readMultiple(sqlQuery, maxSize);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        if (docs == null) {
            docs = new Object[]{};
        }

        try {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("data", ControllerUtils.mapper.readValue(ControllerUtils.mapper.writeValueAsString(docs), ArrayList.class));
            return result;
        } catch (IOException e) {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "error");
            Map<String, Object> error = new HashMap<String, Object>();
            error.put("message", ErrorUtils.escapeJSON(e.getMessage()));
            result.put("error", error);
            return result;
        }
    }

    /**
     * REST GET by ID request handler
     *
     * @param type
     * @param id
     * @return
     * @throws ObjectNotFoundException
     * @throws UnknownTypeException
     */
    @ApiMethod(
            path="/{type}/{id}",
            verb= ApiVerb.GET,
            description="Read entry from space with the provided id",
            produces = { MediaType.APPLICATION_JSON_VALUE }
    )
    @RequestMapping(value = "/{type}/{id}", method = RequestMethod.GET, produces = { MediaType.APPLICATION_JSON_VALUE })
    public
    @ResponseBody
    Map<String, Object> getById(
            @PathVariable @ApiParam(name = "type", paramType = ApiParamType.PATH, description = TYPE_DESCRIPTION) String type,
            @PathVariable @ApiParam(name = "id", paramType = ApiParamType.PATH) String id) throws ObjectNotFoundException {
        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        //read by id request
        Object typedBasedId = getTypeBasedIdObject(gigaSpace, type, id);
        if (logger.isLoggable(Level.FINE))
            logger.fine("creating readbyid query with type: " + type + " and id: " + id);
        IdQuery<Object> idQuery = new IdQuery<Object>(type, typedBasedId);
        Object doc;
        try {
            doc = gigaSpace.readById(idQuery);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        if (doc == null) {
            doc = emptyObject;
        }

        try {
            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("status", "success");
            result.put("data", ControllerUtils.mapper.readValue(ControllerUtils.mapper.writeValueAsString(doc), LinkedHashMap.class));
            return result;
        } catch (IOException e) {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "error");
            Map<String, Object> error = new HashMap<String, Object>();
            error.put("message", ErrorUtils.escapeJSON(e.getMessage()));
            result.put("error", error);
            return result;
        }
    }

    /**
     * REST COUNT request handler
     */
    @ApiMethod(
            path="/{type}/count",
            verb= ApiVerb.GET,
            description="Returns the number of entries in space of the specified type\n"
    )
    @RequestMapping(value = "/{type}/count", method = RequestMethod.GET)
    public
    @ResponseBody
    Map<String, Object> count(
            @ApiParam(name = "type", paramType = ApiParamType.PATH, description = TYPE_DESCRIPTION)
            @PathVariable String type) throws ObjectNotFoundException {

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        //read by id request
        Integer cnt;
        try {
            cnt = gigaSpace.count(new SpaceDocument(type));
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        if (cnt == null) {
            cnt = 0;
        }
        Map<String, Object> result = new Hashtable<String, Object>();
        result.put("status", "success");
        result.put("data", cnt);
        return result;
    }

    private Object getTypeBasedIdObject(GigaSpace gigaSpace, String type, String id) {
        SpaceTypeDescriptor typeDescriptor = gigaSpace.getTypeManager().getTypeDescriptor(type);
        if (typeDescriptor == null) {
            throw new TypeNotFoundException(type);
        }

        //Investigate id type
        String idPropertyName = typeDescriptor.getIdPropertyName();
        SpacePropertyDescriptor idProperty = typeDescriptor.getFixedProperty(idPropertyName);
        try {
            return ControllerUtils.convertPropertyToPrimitiveType(id, idProperty.getType(), idPropertyName);
        } catch (UnsupportedTypeException e) {
            throw new UnsupportedTypeException("Only primitive SpaceId is currently supported by RestData") {
            };
        }
    }


    /**
     * REST DELETE by id request handler
     *
     * @param type
     * @param id
     * @return
     * @throws ObjectNotFoundException
     */
    @ApiMethod(
            path="/{type}/{id}",
            verb= ApiVerb.DELETE,
            description="Gets and deletes the entry from space with the provided id."
    )
    @RequestMapping(value = "/{type}/{id}", method = RequestMethod.DELETE)
    public
    @ResponseBody
    Map<String, Object> deleteById(
            @ApiParam(name = "type", description = TYPE_DESCRIPTION, paramType = ApiParamType.PATH)
            @PathVariable String type,
            @ApiParam(name = "id", paramType = ApiParamType.PATH)
            @PathVariable String id) throws ObjectNotFoundException {

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        //take by id
        Object typedBasedId = getTypeBasedIdObject(gigaSpace, type, id);
        if (logger.isLoggable(Level.FINE))
            logger.fine("creating takebyid query with type: " + type + " and id: " + id);
        Object doc;
        try {
            doc = gigaSpace.takeById(new IdQuery<Object>(type, typedBasedId));
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        if (doc == null) {
            doc = emptyObject;
        }

        try {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("data", ControllerUtils.mapper.readValue(ControllerUtils.mapper.writeValueAsString(doc), Map.class));
            return result;
        } catch (IOException e) {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "error");
            Map<String, Object> error = new HashMap<String, Object>();
            error.put("message", ErrorUtils.escapeJSON(e.getMessage()));
            result.put("error", error);
            return result;
        }
    }

    /**
     * REST DELETE by query request handler
     *
     * @param type
     * @param query
     * @return
     */
    @ApiMethod(
            path="/{type}/",
            verb= ApiVerb.DELETE,
            description="Gets and deletes entries from space that matches the query."
    )
    @RequestMapping(value = "/{type}", method = RequestMethod.DELETE)
    public
    @ResponseBody
    Map<String, Object> deleteByQuery(
            @ApiParam(name = "type", description = TYPE_DESCRIPTION, paramType = ApiParamType.PATH)
            @PathVariable String type,
            @ApiParam(name = "query", paramType = ApiParamType.QUERY)
            @RequestParam(value = QUERY_PARAM) String query,
            @ApiParam(name = "max", description = "The maximum number of entries to return. Default is Integer.MAX_VALUE", paramType = ApiParamType.QUERY)
            @RequestParam(value = MAX_PARAM, required = false) Integer max) {
        if (logger.isLoggable(Level.FINE))
            logger.fine("creating take query with type: " + type + " and query: " + query);

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        SQLQuery<Object> sqlQuery = new SQLQuery<Object>(type, query);
        int maxSize = (max == null ? maxReturnValues : max.intValue());
        Object[] docs;
        try {
            docs = gigaSpace.takeMultiple(sqlQuery, maxSize);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        if (docs == null) {
            docs = new Object[]{};
        }

        try {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "success");
            result.put("data", ControllerUtils.mapper.readValue(ControllerUtils.mapper.writeValueAsString(docs), ArrayList.class));
            return result;
        } catch (IOException e) {
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("status", "error");
            Map<String, Object> error = new HashMap<String, Object>();
            error.put("message", ErrorUtils.escapeJSON(e.getMessage()));
            result.put("error", error);
            return result;
        }
    }

    /**
     * REST POST request handler
     *
     * @param type
     * @param reader
     * @return
     * @throws TypeNotFoundException
     */
    @ApiMethod(
            path="/{type}/",
            verb= ApiVerb.POST,
            description="Write one or more entries to the space."
    )
    @RequestMapping(value = "/{type}", method = RequestMethod.POST)
    public
    @ResponseBody
    Map<String, Object> post(
            @ApiParam(name = "type", description = TYPE_DESCRIPTION, paramType = ApiParamType.PATH)
            @PathVariable String type,
            BufferedReader reader)
            throws TypeNotFoundException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("performing post, type: " + type);

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        createAndWriteDocuments(gigaSpace, type, reader, WriteModifiers.UPDATE_OR_WRITE);
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "success");
        return result;
    }

    private RuntimeException translateDataAccessException(GigaSpace gigaSpace, DataAccessException e, String type) {
        if (gigaSpace.getTypeManager().getTypeDescriptor(type) == null) {
            return new TypeNotFoundException(type);
        } else {
            return e;
        }
    }

    /**
     * TypeNotFoundException Handler, returns an error response to the client
     *
     * @param writer
     * @throws IOException
     */
    @ExceptionHandler(TypeNotFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public void resolveTypeDescriptorNotFoundException(TypeNotFoundException e, Writer writer) throws IOException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("type descriptor for typeName: " + e.getTypeName() + " not found, returning error response");

        writer.write(ErrorUtils.toJSON("Type: " + e.getTypeName() + " is not registered in space"));
    }


    /**
     * ObjectNotFoundException Handler, returns an error response to the client
     *
     * @param writer
     * @throws IOException
     */
    @ExceptionHandler(ObjectNotFoundException.class)
    @ResponseStatus(value = HttpStatus.NOT_FOUND)
    public void resolveDocumentNotFoundException(Exception e, Writer writer) throws IOException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("space id query has no results, returning error response: " + e.getMessage());

        writer.write(ErrorUtils.toJSON(e.getMessage()));

    }

    /**
     * DataAcessException Handler, returns an error response to the client
     *
     * @param e
     * @param writer
     * @throws IOException
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public void resolveDataAccessException(Exception e, Writer writer) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received DataAccessException exception", e);

        writer.write(ErrorUtils.toJSON(e));
    }

    @ExceptionHandler(CannotFindSpaceException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public void resolveCannotFindSpaceException(Exception e, Writer writer) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received CannotFindSpaceException exception", e);

        writer.write(ErrorUtils.toJSON(e));
    }

    @ExceptionHandler(TypeAlreadyRegisteredException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public void resoleTypeAlreadyRegisteredException(TypeAlreadyRegisteredException e, Writer writer) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received TypeAlreadyRegisteredException exception", e);

        writer.write(ErrorUtils.toJSON("Type: " + e.getTypeName() + " is already introduced to space"));
    }

    @ExceptionHandler(RestIntroduceTypeException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public void resolveRestIntroduceTypeException(RestIntroduceTypeException e, Writer writer) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received RestIntroduceTypeException exception", e.getMessage());

        writer.write(ErrorUtils.toJSON(e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public void resolveRuntimeException(RuntimeException e, Writer writer) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received RuntimeException exception", e.getMessage());

        writer.write(ErrorUtils.toJSON("Unhandled exception ["+e.getClass()+"]: "+e.toString()));
    }

    @ExceptionHandler(UnsupportedTypeException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public void resolveUnsupportedTypeException(UnsupportedTypeException e, Writer writer) throws IOException {
        if (logger.isLoggable(Level.WARNING))
            logger.log(Level.WARNING, "received UnsupportedTypeException exception", e.getMessage());

        writer.write(ErrorUtils.toJSON(e.getMessage()));
    }
    /**
     * helper method that creates space documents from the httpRequest payload and writes them to space.
     *
     * @param type
     * @param reader
     * @param updateModifiers
     * @throws TypeNotFoundException
     */
    private void createAndWriteDocuments(GigaSpace gigaSpace, String type, BufferedReader reader, WriteModifiers updateModifiers)
            throws TypeNotFoundException {
        logger.info("creating space Documents from payload");
        SpaceDocument[] spaceDocuments = ControllerUtils.createSpaceDocuments(type, reader, gigaSpace);
        if (spaceDocuments != null && spaceDocuments.length > 0) {
            try {
                gigaSpace.writeMultiple(spaceDocuments, Lease.FOREVER, updateModifiers);
            } catch (DataAccessException e) {
                throw translateDataAccessException(gigaSpace, e, type);
            }
            if (logger.isLoggable(Level.FINE))
                logger.fine("wrote space documents to space");
        } else {
            if (logger.isLoggable(Level.FINE))
                logger.fine("did not write anything to space");
        }
    }


}
