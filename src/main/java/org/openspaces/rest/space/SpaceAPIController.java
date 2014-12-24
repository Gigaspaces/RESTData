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


import com.gigaspaces.client.WriteModifiers;
import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpacePropertyDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;
import com.gigaspaces.query.IdQuery;
import com.gigaspaces.query.QueryResultType;
import com.j_spaces.core.UnknownTypeException;
import com.j_spaces.core.client.SQLQuery;
import net.jini.core.lease.Lease;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.space.CannotFindSpaceException;
import org.openspaces.rest.exceptions.ObjectNotFoundException;
import org.openspaces.rest.exceptions.TypeNotFoundException;
import org.openspaces.rest.utils.ControllerUtils;
import org.openspaces.rest.utils.ErrorUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Spring MVC controller for the RESTful Space API
 *
 * usage examples:
 * 	GET:
 * 	    http://localhost:8080/rest/data/Item/_introduce_type?spaceid=customerid
 *
 * 		http://localhost:8080/rest/data/Item/1
 * 		http://192.168.9.47:8080/rest/data/Item/_criteria?q=data2='common'
 *
 *      Limit result size:
 *      http://192.168.9.47:8080/rest/data/Item/_criteria?q=data2='common'&s=10
 *
 *  DELETE:
 *  	curl -XDELETE http://localhost:8080/rest/data/Item/1
 *  	curl -XDELETE http://localhost:8080/rest/data/Item/_criteria?q=id=1
 *
 *      Limit result size:
 *      curl -XDELETE http://localhost:8080/rest/data/Item/_criteria?q=data2='common'&s=5
 *
 *  POST:
 *  	curl -XPOST -d '[{"id":"1", "data":"testdata", "data2":"common", "nestedData" : {"nestedKey1":"nestedValue1"}}, {"id":"2", "data":"testdata2", "data2":"common", "nestedData" : {"nestedKey2":"nestedValue2"}}, {"id":"3", "data":"testdata3", "data2":"common", "nestedData" : {"nestedKey3":"nestedValue3"}}]' http://localhost:8080/rest/data/Item
 *
 *
 * The response is a json object:
 *  On Sucess:
 *      { "status" : "success" }
 *      If there is a data:
 *      {
 *          "status" : "success",
 *          "data" : {...} or [{...}, {...}]
 *      }
 *
 *  On Failure:
 *      If Inner error (TypeNotFound/ObjectNotFound) then
 *      {
 *          "status" : "error",
 *          "error": {
 *              "message": "some error message"
 *          }
 *      }
 *      If it is a XAP exception:
 *       {
 *          "status" : "error",
 *          "error": {
 *              "java.class" : "the exception's class name",
 *              "message": "exception.getMessage()"
 *          }
 *      }
 *
 * @author rafi
 * @since 8.0
 */
@Controller
@RequestMapping(value = "/*")
public class SpaceAPIController {

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
    private static final String SIZE_PARAM = "size";
    private static final String SPACEID_PARAM = "spaceid";

    private int maxReturnValues = Integer.MAX_VALUE;
    private static final Logger logger = Logger.getLogger(SpaceAPIController.class.getName());

    /**
     * redirects to index view
     *
     * @return
     */
    @RequestMapping(value = "/", method = RequestMethod.GET)
    public ModelAndView redirectToIndex() {
        return new ModelAndView("index");
    }


    /**
     * REST GET for inroducing type to space
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
                throw new IllegalStateException("Type: " + type + " is already introduced to space: " + gigaSpace.getName() + "");
            }

            SpaceTypeDescriptor spaceTypeDescriptor = new SpaceTypeDescriptorBuilder(type).idProperty(spaceID)
                    .routingProperty(spaceID).supportsDynamicProperties(true).create();
            gigaSpace.getTypeManager().registerTypeDescriptor(spaceTypeDescriptor);
            result.put("status", "success");
        } catch (IllegalStateException e) {
            result.put("status", "error");
            result.put("error", "{\"message\": \""+ErrorUtils.escapeJSON(e.getMessage())+"\"}");
        }

        return result;
    }


    /**
     * REST GET by query request handler
     *
     * @param type
     * @param query
     * @return
     * @throws ObjectNotFoundException
     */
    @RequestMapping(value = "/{type}", method = RequestMethod.GET)
    public
    @ResponseBody
    Map<String, Object> getByQuery(
            @PathVariable String type,
            @RequestParam(value = QUERY_PARAM, required = false) String query,
            @RequestParam(value = SIZE_PARAM, required = false) Integer size) throws ObjectNotFoundException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("creating read query with type: " + type + " and query: " + query);

        if (query == null) {
            query = ""; //Query all the data
        }

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        SQLQuery<SpaceDocument> sqlQuery = new SQLQuery<SpaceDocument>(type, query, QueryResultType.DOCUMENT);
        int maxSize = (size == null ? maxReturnValues : size.intValue());
        SpaceDocument[] docs;
        try {
            docs = gigaSpace.readMultiple(sqlQuery, maxSize);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        Map<String, Object>[] data;
        if (docs == null || docs.length == 0) {
            throw new ObjectNotFoundException("no objects matched the criteria");
        }
        data = ControllerUtils.createPropertiesResult(docs);

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "success");
        result.put("data", data);
        return result;
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
    @RequestMapping(value = "/{type}/{id}", method = RequestMethod.GET)
    public
    @ResponseBody
    Map<String, Object> getById(
            @PathVariable String type,
            @PathVariable String id) throws ObjectNotFoundException {
        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        //read by id request
        Object typedBasedId = getTypeBasedIdObject(gigaSpace, type, id);
        if (logger.isLoggable(Level.FINE))
            logger.fine("creating readbyid query with type: " + type + " and id: " + id);
        IdQuery<SpaceDocument> idQuery = new IdQuery<SpaceDocument>(type, typedBasedId, QueryResultType.DOCUMENT);
        SpaceDocument doc;
        try {
            doc = gigaSpace.readById(idQuery);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        if (doc == null) {
            throw new ObjectNotFoundException("no object matched the criteria");
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status","success");
        result.put("data", doc.getProperties());

        return result;
    }

    /**
     * REST COUNT request handler
     */
    @RequestMapping(value = "/{type}/count", method = RequestMethod.GET)
    public
    @ResponseBody
    Map<String, Object> count(
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
            throw new ObjectNotFoundException("no object matched the criteria");
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
        } catch (UnknownTypeException e) {
            throw new DataAccessException("Only primitive SpaceId is currently supported by RestData") {
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
    @RequestMapping(value = "/{type}/{id}", method = RequestMethod.DELETE)
    public
    @ResponseBody
    Map<String, Object> deleteById(
            @PathVariable String type,
            @PathVariable String id) throws ObjectNotFoundException {

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        //take by id
        Object typedBasedId = getTypeBasedIdObject(gigaSpace, type, id);
        if (logger.isLoggable(Level.FINE))
            logger.fine("creating takebyid query with type: " + type + " and id: " + id);
        SpaceDocument doc;
        try {
            doc = gigaSpace.takeById(new IdQuery<SpaceDocument>(type, typedBasedId, QueryResultType.DOCUMENT));
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        if (doc == null) {
            throw new ObjectNotFoundException("no object matched the criteria");
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status","success");
        result.put("data", doc.getProperties());
        return result;
    }

    /**
     * REST DELETE by query request handler
     *
     * @param type
     * @param query
     * @return
     */
    @RequestMapping(value = "/{type}", method = RequestMethod.DELETE)
    public
    @ResponseBody
    Map<String, Object> deleteByQuery(
            @PathVariable String type,
            @RequestParam(value = QUERY_PARAM) String query,
            @RequestParam(value = SIZE_PARAM, required = false) Integer size) {
        if (logger.isLoggable(Level.FINE))
            logger.fine("creating take query with type: " + type + " and query: " + query);

        GigaSpace gigaSpace = ControllerUtils.xapCache.get();
        SQLQuery<SpaceDocument> sqlQuery = new SQLQuery<SpaceDocument>(type, query, QueryResultType.DOCUMENT);
        int maxSize = (size == null ? maxReturnValues : size.intValue());
        SpaceDocument[] docs;
        try {
            docs = gigaSpace.takeMultiple(sqlQuery, maxSize);
        } catch (DataAccessException e) {
            throw translateDataAccessException(gigaSpace, e, type);
        }

        Map<String, Object> result = new HashMap<String, Object>();
        result.put("status", "success");
        result.put("data", ControllerUtils.createPropertiesResult(docs));
        return result;
    }

    /**
     * REST POST request handler
     *
     * @param type
     * @param reader
     * @return
     * @throws TypeNotFoundException
     */
    @RequestMapping(value = "/{type}", method = RequestMethod.POST)
    public
    @ResponseBody
    Map<String, Object> post(
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
    public void resolveDocumentNotFoundException(Writer writer) throws IOException {
        if (logger.isLoggable(Level.FINE))
            logger.fine("space id query has no results, returning error response");

        writer.write(ErrorUtils.toJSON("Object not found"));

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
