package org.openspaces.rest.tests;
import java.io.BufferedReader;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openspaces.admin.Admin;
import org.openspaces.admin.AdminFactory;
import org.openspaces.admin.space.Spaces;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceConfigurer;
import org.openspaces.core.space.UrlSpaceConfigurer;
import org.openspaces.rest.exceptions.ObjectNotFoundException;
import org.openspaces.rest.exceptions.TypeNotFoundException;
import org.openspaces.rest.space.SpaceAPIController;
import org.openspaces.rest.utils.ControllerUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.mvc.multiaction.NoSuchRequestHandlingMethodException;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;
import com.gigaspaces.query.IdQuery;
import com.gigaspaces.query.QueryResultType;


public class SpaceAPIControllerTest {

    public static enum Job{WORKER, DOCTOR, FARMER}
    
    private static final String SPACENAME = "embeddedTestSpace";
    private static SpaceAPIController spaceAPIController;
    private static GigaSpace gigaSpace;
    private static String locators;
    private static String groups;
    static {
    	try{
    		locators =InetAddress.getLocalHost().getHostAddress();

        }catch(Exception e){}

        groups = "openspaces-rest";
    }


    @BeforeClass
    public static void beforeClass(){
        spaceAPIController = new SpaceAPIController();
        ControllerUtils.spaceName = SPACENAME;
        ControllerUtils.lookupGroups = groups;
        gigaSpace = new GigaSpaceConfigurer(new UrlSpaceConfigurer("/./" + SPACENAME+"?groups="+groups)).gigaSpace();
        registerProductType(gigaSpace);
    }
    
    @Before
    public void beforeTest(){
        gigaSpace.clear(null);
    }

    @Test
    public void testInroduceType() throws Exception {
        String content = "[{\"CatalogNumber\":\"doc1\", \"Category\":\"Hardware\", \"Name\":\"Anvil1\", \"nested\": {\"nestedVar1\":\"nestedValue1\"}}, {\"CatalogNumber\":\"doc2\", \"Category\":\"Hardware\", \"Name\":\"Anvil2\"}]";
        try {
            Map<String, Object> postResult = spaceAPIController.post("MyType", new BufferedReader(new StringReader(content)));
            Assert.fail("Writing to the space without introducing the class should cause TypeNotFoundException and it didn't");
        } catch (TypeNotFoundException e) {
            //This is the right behavior
        }
        Map<String, Object> introduceTypeResult = spaceAPIController.introduceType("MyType", "CatalogNumber");
        Assert.assertEquals("success",introduceTypeResult.get("status"));

        try {
            Map<String, Object> postResult = spaceAPIController.post("MyType", new BufferedReader(new StringReader(content)));
            Assert.assertEquals("Excpecting to get status equals to success", "success", postResult.get("status"));
            // More tests for write are in testGet()
        } catch (TypeNotFoundException e) {
            Assert.fail("Write should not throw TypeNotFoundException after introducing the class");
        }
    }

    @Test
    public void testGet() throws Exception {
        //write first doc
        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("CatalogNumber", "doc1");
        properties1.put("Category", "Hardware");
        properties1.put("Name", "Anvil1");
        properties1.put("Price", 9.99f);
        properties1.put("Tags", new String[] {"heavy", "anvil"});
        properties1.put("testvar1", "value1");

        Map<String, Object> nestedProps = new HashMap<String, Object>();
        nestedProps.put("nestedVar1", "nestedValue1");
        properties1.put("nested", nestedProps);
        SpaceDocument document = new SpaceDocument("Product", properties1);
        gigaSpace.write(document);

        //write second doc
        Map<String, Object> properties2 = new HashMap<String, Object>();
        properties2.put("CatalogNumber", "doc2");
        properties2.put("Category", "Hardware");
        properties2.put("Name", "Anvil2");
        properties2.put("Price", 9.99f);
        properties2.put("Tags", new String[] {"heavy", "anvil"});
        properties2.put("testvar2", "value2");

        SpaceDocument document2 = new SpaceDocument("Product", properties2);
        gigaSpace.write(document2);

        //test get by type
        Map<String, Object> result = spaceAPIController.getByQuery("Product", "", Integer.MAX_VALUE);
        Assert.assertEquals("success", result.get("status"));
        Map<String, Object>[] resultData = (Map<String, Object>[]) result.get("data");
        Assert.assertEquals(2, resultData.length);
//        compareMaps(properties1, resultType[0]);
//        compareMaps(properties2, resultType[1]);

        //test get by var1
        result = spaceAPIController.getByQuery("Product", "testvar1='value1'", Integer.MAX_VALUE);
        Assert.assertEquals("success", result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        
        Assert.assertEquals(1, resultData.length);
        compareMaps(properties1, resultData[0]);

        //null size limit
        result = spaceAPIController.getByQuery("Product", "testvar1='value1'", null);
        Assert.assertEquals("success", result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        Assert.assertEquals(1, resultData.length);
        compareMaps(properties1, resultData[0]);

        //1 size limit
        result = spaceAPIController.getByQuery("Product", "testvar1='value1'", 1);
        Assert.assertEquals("success", result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        Assert.assertEquals(1, resultData.length);
        compareMaps(properties1, resultData[0]);

        //test get by var2
        result = spaceAPIController.getByQuery("Product", "testvar2='value2'", Integer.MAX_VALUE);
        Assert.assertEquals("success", result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        Assert.assertEquals(1, resultData.length);
        compareMaps(properties2, resultData[0]);

        //test nested
        result = spaceAPIController.getByQuery("Product", "nested.nestedVar1='nestedValue1'", Integer.MAX_VALUE);
        Assert.assertEquals("success", result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        Assert.assertEquals(1, resultData.length);
        compareMaps(properties1, resultData[0]);

        //test pojo to document
        Pojo pojo1 = new Pojo();
        pojo1.setId(String.valueOf(1));
        pojo1.setVal("val1");
        gigaSpace.write(pojo1);

        String pojoClassName = Pojo.class.getName();
        result = spaceAPIController.getByQuery(pojoClassName, "id='1'", Integer.MAX_VALUE);
        Assert.assertEquals("success", result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        comparePojoAndProps(pojo1, resultData[0]);

        Map<String, Object> resultByID = spaceAPIController.getById(pojoClassName, "1");
        Assert.assertEquals("success", resultByID.get("status"));
        Map<String, Object> resultDataById = (Map<String, Object>) resultByID.get("data");
        comparePojoAndProps(pojo1, resultDataById);

        resultByID = spaceAPIController.getById("Product", "doc1");
        Assert.assertEquals("success", resultByID.get("status"));
        resultDataById = (Map<String, Object>) resultByID.get("data");
        compareMaps(properties1, resultDataById);

        Pojo2 pojo2 = new Pojo2();
        pojo2.setId(1);
        pojo2.setVal(123L);
        gigaSpace.write(pojo2);

        resultByID = spaceAPIController.getById(Pojo2.class.getName(), "1");
        Assert.assertEquals("success", resultByID.get("status"));
        resultDataById = (Map<String, Object>) resultByID.get("data");
        comparePojoAndProps(pojo2, resultDataById);

        Pojo3 pojo3 = new Pojo3();
        pojo3.setId(1F);
        pojo3.setVal(123L);
        gigaSpace.write(pojo3);

        resultByID = spaceAPIController.getById(Pojo3.class.getName(), "1");
        Assert.assertEquals("success", resultByID.get("status"));
        resultDataById = (Map<String, Object>) resultByID.get("data");
        comparePojoAndProps(pojo3, resultDataById);
    }

    @Test
    public void testDelete() throws Exception {
        
        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("CatalogNumber", "doc1");
        properties1.put("Category", "Hardware");
        properties1.put("Name", "Anvil1");
        SpaceDocument document = new SpaceDocument("Product", properties1);
        gigaSpace.write(document);

        Map<String, Object> properties2 = new HashMap<String, Object>();
        properties2.put("CatalogNumber", "doc2");
        properties2.put("Category", "Hardware");
        properties2.put("Name", "Anvil2");
        Map<String, Object> nestedProps = new HashMap<String, Object>();
        nestedProps.put("nestedVar1", "nestedValue1");
        properties2.put("nested", nestedProps);
        SpaceDocument document2 = new SpaceDocument("Product", properties2);
        gigaSpace.write(document2);

        Map<String, Object> properties3 = new HashMap<String, Object>();
        properties3.put("CatalogNumber", "doc3");
        properties3.put("Category", "Hardware");
        properties3.put("Name", "Anvil3");
        SpaceDocument document3 = new SpaceDocument("Product", properties3);
        gigaSpace.write(document3);

        Assert.assertEquals(3, gigaSpace.count(null));

        //test delete by type
        Map<String, Object> result = spaceAPIController.deleteByQuery("Product", "", Integer.MAX_VALUE);
        Assert.assertEquals("success",result.get("status"));
        Map<String, Object>[] resultData = (Map<String, Object>[]) result.get("data");
        Assert.assertEquals(3, resultData.length);

        Assert.assertEquals(0, gigaSpace.count(null));
        
        gigaSpace.write(document);
        gigaSpace.write(document2);
        gigaSpace.write(document3);
        
        Assert.assertEquals(3, gigaSpace.count(null));
        
        //test simple delete
        result = spaceAPIController.deleteByQuery("Product", "Name='Anvil1'", Integer.MAX_VALUE);
        Assert.assertEquals("success",result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        compareMaps(properties1, resultData[0]);
        Assert.assertEquals(2, gigaSpace.count(null));

        //test nested delete
        result = spaceAPIController.deleteByQuery("Product", "nested.nestedVar1='nestedValue1'", Integer.MAX_VALUE);
        Assert.assertEquals("success",result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        compareMaps(properties2, resultData[0]);
        Assert.assertEquals(1, gigaSpace.count(null));

        //test id-based delete
        Map<String, Object> resultById = spaceAPIController.deleteById("Product", "doc3");
        Assert.assertEquals("success",result.get("status"));
        Map<String, Object> resultDataById = (Map<String, Object>) resultById.get("data");
        compareMaps(properties3, resultDataById);
        Assert.assertEquals(0, gigaSpace.count(null));

        Pojo pojo1 = new Pojo();
        pojo1.setId(String.valueOf(1));
        pojo1.setVal("val1");
        gigaSpace.write(pojo1);
        Assert.assertEquals(1, gigaSpace.count(null));

        //test delete pojo by id
        resultById = spaceAPIController.deleteById(Pojo.class.getName(), "1");
        Assert.assertEquals("success",resultById.get("status"));
        resultDataById = (Map<String, Object>) resultById.get("data");
        comparePojoAndProps(pojo1, resultDataById);
        Assert.assertEquals(0, gigaSpace.count(null));

        gigaSpace.write(pojo1);

        result = spaceAPIController.deleteByQuery(Pojo.class.getName(), "id='1'", Integer.MAX_VALUE);
        Assert.assertEquals("success",result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        comparePojoAndProps(pojo1, resultData[0]);
        Assert.assertEquals(0, gigaSpace.count(null));

        //
        Pojo2 pojo2 = new Pojo2();
        pojo2.setId(1);
        pojo2.setVal(123L);
        gigaSpace.write(pojo2);

        resultById = spaceAPIController.deleteById(Pojo2.class.getName(), "1");
        Assert.assertEquals("success",resultById.get("status"));
        Assert.assertEquals(0, gigaSpace.count(new Pojo2()));

        Pojo3 pojo3 = new Pojo3();
        pojo3.setId(1F);
        pojo3.setVal(123L);
        gigaSpace.write(pojo3);

        resultById = spaceAPIController.deleteById(Pojo3.class.getName(), "1");
        Assert.assertEquals("success",resultById.get("status"));
        Assert.assertEquals(0, gigaSpace.count(new Pojo2()));
    }

    @Test
    public void testPost() throws Exception {
        //write first doc
        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("CatalogNumber", "doc1");
        properties1.put("Category", "Hardware");
        properties1.put("Name", "Anvil1");

        Map<String, Object> nestedProps = new HashMap<String, Object>();
        nestedProps.put("nestedVar1", "nestedValue1");
        properties1.put("nested", nestedProps);

        Map<String, Object> properties2 = new HashMap<String, Object>();
        properties2.put("CatalogNumber", "doc2");
        properties2.put("Category", "Hardware");
        properties2.put("Name", "Anvil2");

        //test writemultiple
        String content = "[{\"CatalogNumber\":\"doc1\", \"Category\":\"Hardware\", \"Name\":\"Anvil1\", \"nested\": {\"nestedVar1\":\"nestedValue1\"}}, {\"CatalogNumber\":\"doc2\", \"Category\":\"Hardware\", \"Name\":\"Anvil2\"}]";

        Map<String, Object> postResult = spaceAPIController.post("Product", new BufferedReader(new StringReader(content)));
        Assert.assertEquals("success", postResult.get("status"));

        Assert.assertEquals(2, gigaSpace.count(null));

        Map<String, Object> countResult = spaceAPIController.count("Product");
        Assert.assertEquals("success", countResult.get("status"));
        Assert.assertEquals(2,countResult.get("data"));

        SpaceDocument doc1 = gigaSpace.readById(new IdQuery<SpaceDocument>("Product", "doc1",QueryResultType.DOCUMENT));
        compareMaps(properties1, doc1.getProperties());

        SpaceDocument doc2 = gigaSpace.readById(new IdQuery<SpaceDocument>("Product", "doc2",QueryResultType.DOCUMENT));
        compareMaps(properties2, doc2.getProperties());

        //test update, this should fail since object already exists
        Map<String, Object> properties1copy = new HashMap<String, Object>(properties1);
        Map<String, Object> nestedPropsCopy = new HashMap<String, Object>(nestedProps);
        properties1copy.put("Name", "Anvil1new");
        nestedPropsCopy.put("nestedVar1", "nestedValue1new");
        properties1copy.put("nested", nestedPropsCopy);

        content = "[{\"CatalogNumber\":\"doc1\", \"Category\":\"Hardware\", \"Name\":\"Anvil1new\", \"nested\": {\"nestedVar1\":\"nestedValue1new\"}}, {\"CatalogNumber\":\"doc2\", \"Category\":\"Hardware\", \"Name\":\"Anvil2new\"}]";
        try{
            Map<String, Object> result = spaceAPIController.post("Product", new BufferedReader(new StringReader(content)));
            Assert.assertEquals("success", result.get("status"));
        }catch(Exception e){
            Assert.fail("An action should not cause an exception but it did");
        }

        Assert.assertEquals(2, gigaSpace.count(null));
        doc1 = gigaSpace.readById(new IdQuery<SpaceDocument>("Product", "doc1",QueryResultType.DOCUMENT));
        //compare docs and see that the document before the update operation is the result
        compareMaps(properties1copy, doc1.getProperties());


        //
        Pojo2 pojo2 = new Pojo2();
        gigaSpace.snapshot(pojo2);
        pojo2.setId(1);
        pojo2.setVal(123L);

        content = "[{\"id\":\"1\", \"val\":\"123\"}]";
        Map<String, Object> result = spaceAPIController.post(Pojo2.class.getName(), new BufferedReader(new StringReader(content)));
        Assert.assertEquals("success", result.get("status"));

        SpaceDocument docresult = gigaSpace.readById(new IdQuery<SpaceDocument>(Pojo2.class.getName(), 1,QueryResultType.DOCUMENT));
        comparePojoAndProps(pojo2, docresult.getProperties());

        Pojo3 pojo3 = new Pojo3();
        gigaSpace.snapshot(pojo3);
        pojo3.setId(1F);
        pojo3.setVal(123L);

        content = "[{\"id\":\"1\", \"val\":\"123\"}]";
        result = spaceAPIController.post(Pojo3.class.getName(),new  BufferedReader(new StringReader(content)));
        Assert.assertEquals("success", result.get("status"));

        SpaceDocument docresult2 = gigaSpace.readById(new IdQuery<SpaceDocument>(Pojo3.class.getName(), 1F,QueryResultType.DOCUMENT));
        comparePojoAndProps(pojo3, docresult2.getProperties());
    }

    @Test
    public void testUpdate() throws MissingServletRequestParameterException, HttpMediaTypeNotAcceptableException, NoSuchRequestHandlingMethodException, TypeNotFoundException{
        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("CatalogNumber", "doc1");
        properties1.put("Category", "Hardware");
        properties1.put("Name", "Anvil1");
        Map<String, Object> nestedProps = new HashMap<String, Object>();
        nestedProps.put("nestedVar1", "nestedValue1");
        properties1.put("nested", nestedProps);

        Map<String, Object> properties2 = new HashMap<String, Object>();
        properties2.put("CatalogNumber", "doc2");
        properties2.put("Category", "Hardware");
        properties2.put("Name", "Anvil2");

        String content = "[{\"CatalogNumber\":\"doc1\", \"Category\":\"Hardware\", \"Name\":\"Anvil1\", \"nested\": {\"nestedVar1\":\"nestedValue1\"}}, {\"CatalogNumber\":\"doc2\", \"Category\":\"Hardware\", \"Name\":\"Anvil2\"}]";

        Map<String, Object> result = spaceAPIController.post("Product", new BufferedReader(new StringReader(content)));
        Assert.assertEquals("success", result.get("status"));

        Assert.assertEquals(2, gigaSpace.count(null));

        //test update multiple
        properties1.put("Name", "Anvil1new");
        nestedProps.put("nestedVar1", "nestedValue1new");
        properties1.put("nested", nestedProps);

        properties2.put("Name", "Anvil2new");

        content = "[{\"CatalogNumber\":\"doc1\", \"Category\":\"Hardware\", \"Name\":\"Anvil1new\", \"nested\": {\"nestedVar1\":\"nestedValue1new\"}}, {\"CatalogNumber\":\"doc2\", \"Category\":\"Hardware\", \"Name\":\"Anvil2new\"}]";
        result = spaceAPIController.post("Product", new BufferedReader(new StringReader(content)));
        Assert.assertEquals("success", result.get("status"));

        Assert.assertEquals(2, gigaSpace.count(null));
        SpaceDocument doc1 = gigaSpace.readById(new IdQuery<SpaceDocument>("Product", "doc1",QueryResultType.DOCUMENT));
        compareMaps(properties1, doc1.getProperties());

        SpaceDocument doc2 = gigaSpace.readById(new IdQuery<SpaceDocument>("Product", "doc2",QueryResultType.DOCUMENT));
        compareMaps(properties2, doc2.getProperties());

        //
        Pojo2 pojo2 = new Pojo2();
        gigaSpace.snapshot(pojo2);
        pojo2.setId(1);
        pojo2.setVal(123L);

        content = "[{\"id\":\"1\", \"val\":\"123\"}]";
        result = spaceAPIController.post(Pojo2.class.getName(), new BufferedReader(new StringReader(content)));
        Assert.assertEquals("success", result.get("status"));

        SpaceDocument docresult = gigaSpace.readById(new IdQuery<SpaceDocument>(Pojo2.class.getName(), 1,QueryResultType.DOCUMENT));
        comparePojoAndProps(pojo2, docresult.getProperties());

        Pojo3 pojo3 = new Pojo3();
        gigaSpace.snapshot(pojo3);
        pojo3.setId(1F);
        pojo3.setVal(123L);

        content = "[{\"id\":\"1\", \"val\":\"123\"}]";
        result = spaceAPIController.post(Pojo3.class.getName(), new BufferedReader(new StringReader(content)));
        Assert.assertEquals("success", result.get("status"));

        SpaceDocument docresult2 = gigaSpace.readById(new IdQuery<SpaceDocument>(Pojo3.class.getName(), 1F,QueryResultType.DOCUMENT));
        comparePojoAndProps(pojo3, docresult2.getProperties());
    }

    @Test
    public void testPojoDocInterop() throws ObjectNotFoundException, TypeNotFoundException{
        //test pojo to document
        Pojo2 nestedPojo = new Pojo2();
        nestedPojo.setId(22);
        nestedPojo.setVal(123L);

        Pojo pojo1 = new Pojo();
        pojo1.setId(String.valueOf(1));
        pojo1.setVal("val1");
        pojo1.setNestedObj(nestedPojo);
        gigaSpace.write(pojo1);

        Map<String, Object> nestedProps = new HashMap<String, Object>();
        nestedProps.put("id", 22);
        nestedProps.put("val", 123L);

        Map<String, Object> properties1 = new HashMap<String, Object>();
        properties1.put("id", "2");
        properties1.put("val", "val2");
        properties1.put("nestedObj", nestedProps);

        String content = "[{\"id\":\"2\", \"val\":\"val2\", \"nestedObj\": {\"id\":\"22\", \"val\":\"123\"}}]";
        Map<String, Object> result = spaceAPIController.post(Pojo.class.getName(), new BufferedReader(new StringReader(content)));
        Assert.assertEquals("success", result.get("status"));

        SpaceDocument doc = gigaSpace.readById(new IdQuery<SpaceDocument>(Pojo.class.getName(), "2",QueryResultType.DOCUMENT));
        compareMaps(properties1, doc.getProperties());
    }

    @Test
    public void testEnum() throws ObjectNotFoundException, TypeNotFoundException{

        //write first doc
        SpaceDocument document = new SpaceDocument("Person")
            .setProperty("ID", "111")
            .setProperty("Job", Job.DOCTOR);
        gigaSpace.write(document);

        //write second doc
        SpaceDocument document2 = new SpaceDocument("Person")
            .setProperty("ID", "222")
            .setProperty("Job", Job.FARMER);
        gigaSpace.write(document2);

        BufferedReader reader = new BufferedReader(new StringReader("{\"ID\":\"333\", \"Job\":\"WORKER\"}"));
        Map<String, Object> postResult = spaceAPIController.post("Person", reader);
        Assert.assertEquals("success", postResult.get("status"));

        Map<String, Object> result = spaceAPIController.getByQuery("Person", "Job='DOCTOR'", Integer.MAX_VALUE);
        Assert.assertEquals("success", result.get("status"));
        Map<String, Object>[] resultData = (Map<String, Object>[]) result.get("data");
        compareMaps(document.getProperties(), resultData[0]);

        result = spaceAPIController.getByQuery("Person","Job='WORKER'", Integer.MAX_VALUE);
        Assert.assertEquals("success", result.get("status"));
        resultData = (Map<String, Object>[]) result.get("data");
        Map<String, Object> expected = new HashMap<String, Object>();
        expected.put("ID", "333");
        expected.put("Job", Job.WORKER);
        compareMaps(expected, resultData[0]);
    }
    
    @Test(expected=TypeNotFoundException.class) 
    public void testTypeNotRegisteredOnPut() throws Exception {
        String content = "[{\"id\":\"1\", \"val\":\"123\"}]";
        spaceAPIController.post(UnregisteredPojo.class.getName(), new BufferedReader(new StringReader(content)));
    }

    @Test(expected=TypeNotFoundException.class) 
    public void testTypeNotRegisteredOnPost() throws Exception {
        String content = "[{\"id\":\"1\", \"val\":\"123\"}]";
        spaceAPIController.post(UnregisteredPojo.class.getName(), new  BufferedReader(new StringReader(content)));
    }
    
    @Test(expected=TypeNotFoundException.class) 
    public void testTypeNotFoundOnGetByQuery() throws ObjectNotFoundException {
        spaceAPIController.getByQuery("IDontExist","id = 123", 1);
    }

    @Test(expected=TypeNotFoundException.class) 
    public void testTypeNotFoundOnGetById() throws ObjectNotFoundException {
        spaceAPIController.getById("IDontExist", "123");
    }
    
    @Test(expected=TypeNotFoundException.class) 
    public void testTypeNotFoundOnGetByType() throws ObjectNotFoundException {
        spaceAPIController.getByQuery("IDontExist", "", 1);
    }

    @Test(expected=TypeNotFoundException.class) 
    public void testTypeNotFoundOnDeleteById() throws ObjectNotFoundException {
        spaceAPIController.deleteById("IDontExist", "123");
    }

    @Test(expected=TypeNotFoundException.class) 
    public void testTypeNotFoundOnDeleteByQuery() throws ObjectNotFoundException {
        spaceAPIController.deleteByQuery("IDontExist", "id = 123", 1);
    }

    @Test(expected=TypeNotFoundException.class) 
    public void testTypeNotFoundOnDeleteByType() throws ObjectNotFoundException {
        spaceAPIController.deleteByQuery("IDontExist", "", 1);
    }
    
    private static void comparePojoAndProps(IPojo pojo, Map<String, Object> resultMap){
        Assert.assertEquals(pojo.getId(), resultMap.get("id"));
        Assert.assertEquals(pojo.getVal(), resultMap.get("val"));
    }
    
    private static void registerProductType(GigaSpace gigaspace) {
        // Create type descriptor:
        SpaceTypeDescriptor typeDescriptor = new SpaceTypeDescriptorBuilder("Product")
        .idProperty("CatalogNumber")
        .routingProperty("Category")
        .create();
        // Register type:
        gigaspace.getTypeManager().registerTypeDescriptor(typeDescriptor);
        
        // Create type descriptor:
        typeDescriptor = new SpaceTypeDescriptorBuilder("Person")
        .idProperty("ID")
        .addFixedProperty("Job", Job.class)
        .create();
        // Register type:
        gigaspace.getTypeManager().registerTypeDescriptor(typeDescriptor);
    }

    private void compareMaps(Map<String, Object> m1, Map<String, Object> m2){
        Assert.assertEquals(m1.keySet(), m2.keySet());
        Set<String> keySet1 = m1.keySet();
        for (String key : keySet1) {
            Object value1 = m1.get(key);
            Object value2 = m2.get(key);
            if (value2 == null){
                Assert.fail("(" + key + ":" + value1 + ") is missing");
            }
            if (value1 instanceof String[]){
                Arrays.equals((String[])value1, (String[])value2);
            }else if (value2 instanceof SpaceDocument){
                compareMaps((Map<String, Object>) value1, ((SpaceDocument)value2).getProperties());
            }
            else{
                Assert.assertEquals(value1, value2);
            }
        }
    }

    public static void main(String[] args)throws Exception{
    	
    	Admin admin=new AdminFactory().addLocator("192.168.137.1").discoverUnmanagedSpaces().useDaemonThreads(true).create();
    	Spaces spaces=admin.getSpaces();
    	spaces.waitFor("embeddedTestSpace");
    	
    }

}
