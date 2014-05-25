[![Build Status](https://secure.travis-ci.org/OpenSpaces/RESTData.png)](http://travis-ci.org/OpenSpaces/RESTData)


<h2>Overview</h2>

<p>&nbsp;The project provides REST support for the GigaSpace Space API using HTTP methods in the following manner:</p>
<ol>
  <li>GET - can be used to perform an introduceType, readByID or a readMultiple action by a space query.</li>
	<li>POST - can be used to perform a write / writeMultiple action.<br/>
		<b>Notice:</b> POST is mapped to a WriteOnly action.<br/>
		An exception will be thrown when trying to write an object which already exists in space.</li>
	<li>PUT - can be used to perform a single or multiple write or update actions.</li>
	<li>DELETE - can be used to perform take / takeMultiple actions either by ID or by a space query.</li>
</ol>


<h2><a name="ProjectDocumentation-Examples"></a>Examples</h2>
Note: The following examples uses <i>myDataGrid</i> as space name.
Note: If the <i>space</i> parameter is not passed, <i>defaultSpaceName</i> defined in config.properties will be used.
<ul>
    <li>IntroduceType
        <ul>
        		<li>&nbsp;<a href="http://localhost:8080/RESTData/rest/data/Item/_introduce_type?space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/_introduce_type?space=myDataGrid</a></li>
        </ul>
    </li>
	<li>WriteMultiple
<br clear="all" />
<div class="preformatted panel" style="border-width: 1px;"><div class="preformattedContent panelContent">
<pre>curl -XPOST -H "Content-Type: application/json" -d '[{"id":"1", "data":"testdata", "data2":"common", "nestedData" : {"nestedKey1":"nestedValue1"}}, {"id":"2", "data":"testdata2", "data2":"common", "nestedData" : {"nestedKey2":"nestedValue2"}}, {"id":"3", "data":"testdata3", "data2":"common", "nestedData" : {"nestedKey3":"nestedValue3"}}]' http://localhost:8080/RESTData/rest/data/Item?space=myDataGrid
</pre>
</div></div></li>
    <li>count
	<ul>
		<li>&nbsp;<a href="http://localhost:8080/RESTData/rest/data/Item/count?space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/count?space=myDataGrid</a></li>
	</ul>
	</li>
	<li>readMultiple
	<ul>
		<li>&nbsp;<a href="http://localhost:8080/RESTData/rest/data/Item/_criteria?q=data2='common'&space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/_criteria?q=data2='common'</a></li>
		<li>&nbsp;<a href="http://localhost:8080/RESTData/rest/data/Item/_criteria?q=id=%271%27%20or%20id=%272%27%20or%20id=%273%27&space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/_criteria?q=id=%271%27%20or%20id=%272%27%20or%20id=%273%27&space=myDataGrid</a> (the url is encoded, the query is "id='1' or id='2' or id='3'")</li>
	</ul>
	</li>
	<li><font color="">readById</font><br clear="all" />
	<ul>
		<li><a href="http://localhost:8080/RESTData/rest/data/Item/1?space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/1?space=myDataGrid</a></li>
		<li><a href="http://localhost:8080/RESTData/rest/data/Item/2?space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/2?space=myDataGrid</a></li>
		<li><a href="http://localhost:8080/RESTData/rest/data/Item/3?space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/3?space=myDataGrid</a></li>
	</ul>
	</li>
	<li><font color="#000000">updateMultiple</font><div class="preformatted panel" style="border-width: 1px;"><div class="preformattedContent panelContent">
<pre>curl -XPUT -H "Content-Type: application/json" -d '[{"id":"1", "data":"testdata", "data2":"commonUpdated", "nestedData" : {"nestedKey1":"nestedValue1"}}, {"id":"2", "data":"testdata2", "data2":"commonUpdated", "nestedData" : {"nestedKey2":"nestedValue2"}}, {"id":"3", "data":"testdata3", "data2":"commonUpdated", "nestedData" : {"nestedKey3":"nestedValue3"}}]' http://localhost:8080/RESTData/rest/data/Item?space=myDataGrid
</pre>
</div></div>-> see that data2 field is updated: <a href="http://localhost:8080/RESTData/rest/data/Item/_criteria?q=data2='commonUpdated'&space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/_criteria?q=data2='commonUpdated'&space=myDataGrid</a></li>
</ul>


<ul>
	<li>&nbsp;<font color="#000000">single nested update</font><div class="preformatted panel" style="border-width: 1px;"><div class="preformattedContent panelContent">
<pre>curl -XPUT -H "Content-Type: application/json" -d '{"id":"1", "data":"testdata", "data2":"commonUpdated", "nestedData" : {"nestedKey1":"nestedValue1Updated"}}' http://localhost:8080/RESTData/rest/data/Item?space=myDataGrid
</pre>
</div></div>-> <font color="#000000">see that Item1 nested field is updated:</font><font color="#000000">&nbsp;</font>http://localhost:8080/RESTData/rest/data/Item/1?space=myDataGrid</li>
</ul>


<ul>
	<li><font color="#000000">takeMultiple (url is encoded, the query is "id=1 or id=2")</font><div class="preformatted panel" style="border-width: 1px;"><div class="preformattedContent panelContent">
<pre>curl -XDELETE http://localhost:8080/RESTData/rest/data/Item/_criteria?q=id=%271%27%20or%20id=%272%27&space=myDataGrid
</pre>
</div></div>-> see that only Item3 remains: <a href="http://localhost:8080/RESTData/rest/data/Item/_criteria?q=id=%271%27%20or%20id=%272%27%20or%20id=%273%27&space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/_criteria?q=id=%271%27%20or%20id=%272%27%20or%20id=%273%27&space=myDataGrid</a></li>
</ul>


<ul>
	<li><font color="#000000">takeById</font><div class="preformatted panel" style="border-width: 1px;"><div class="preformattedContent panelContent">
<pre>curl -XDELETE "http://localhost:8080/RESTData/rest/data/Item/3?space=myDataGrid"
</pre>
</div></div><br/>
-> see that Item3 does not exists: <a href="http://localhost:8080/RESTData/rest/data/Item/_criteria?q=id=%271%27%20or%20id=%272%27%20or%20id=%273%27&space=myDataGrid" rel="nofollow">http://localhost:8080/RESTData/rest/data/Item/_criteria?q=id=%271%27%20or%20id=%272%27%20or%20id=%273%27&space=myDataGrid</a></li>
</ul>


<h2><a name="ProjectDocumentation-SetupInstructions"></a>Setup Instructions</h2>

<p>1.download the project from Github</p>

<p>2.edit "/RESTData/src/main/webapp/WEB-INF/config.properties" to include your space name, for example: <tt>defaultSpaceName=testSpace</tt></p>
Note: This is a default space name. You can override it by passing <i>space</i> parameter to the request. 

<p>3.package the project using maven: "mvn package"<br/>
this will run the unit tests and package the project to a war file located at /target/RESTData.war</p>

<p>4.deploy the war file. </p>
