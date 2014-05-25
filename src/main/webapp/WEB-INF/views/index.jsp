<%@page import="java.net.InetAddress"%>
<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
	pageEncoding="ISO-8859-1"%>
<% String ip = InetAddress.getLocalHost().getHostAddress();
   String context = request.getContextPath();
   String spaceNameParam = request.getParameter("spacename");
   String spaceName = "myDataGrid";
   if (!(spaceNameParam == null || spaceNameParam.equals(""))) {
       spaceName = spaceNameParam;
   }
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>GigaSpaces REST API</title>
</head>
<body>
	<center><img alt="logo" src="<%=context%>/resources/logo.png"></center><br/>

	<h1>REST Data (Space API)</h1>
	<h2>Examples</h2>
	<h3>follow the steps one by one to experience the whole feature set</h3>
    Note: The following examples use <i>myDataGrid</i> as space name by passing it as parameter.
    <form>
        <br/>You can change it here:     <input name="spacename" value="<%=spaceName%>">
        <input type="submit" value="Update this page">
    </form>
	<ul>
        <li> introduceType
            <ul>
                <li><p><a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/_introduce_type?space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/_introduce_type?space=<%=spaceName%></a></p></li>
            </ul>
        </li>
		<li> writeMultiple
		<ul>
			<li><p>curl -XPOST -H "Content-Type: application/json" -d '[{"id":"1", "data":"testdata", "data2":"common", "nestedData" : {"nestedKey1":"nestedValue1"}}, {"id":"2", "data":"testdata2", "data2":"common", "nestedData" : {"nestedKey2":"nestedValue2"}}, {"id":"3", "data":"testdata3", "data2":"common", "nestedData" : {"nestedKey3":"nestedValue3"}}]' http://<%=ip%>:8080<%=context%>/rest/data/Item?space=<%=spaceName%></p></li>
		</ul>
		</li>
        <li> count
            <ul>
                <li><p><a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/count?space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/count?space=<%=spaceName%></a></p></li>
            </ul>
        </li>
		<li> readMultiple
		<ul>
			<li><p><a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=data2='common'&space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=data2='common'&space=<%=spaceName%></a></p></li>
			<li><p><a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=id='1' or id='2' or id='3'&space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=id='1' or id='2' or id='3'&space=<%=spaceName%></a></p></li>
		</ul>
		</li>
		<li> readById
		<ul>
			<li><p><a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/1?space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/1?space=<%=spaceName%></a></p></li>
			<li><p><a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/2?space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/2?space=<%=spaceName%></a></p></li>
			<li><p><a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/3?space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/3?space=<%=spaceName%></a></p></li>
		</ul>
		</li>
		<li> updateMultiple
		<ul>
			<li><p>curl -XPUT -H "Content-Type: application/json" -d '[{"id":"1", "data":"testdata", "data2":"commonUpdated", "nestedData" : {"nestedKey1":"nestedValue1"}}, {"id":"2", "data":"testdata2", "data2":"commonUpdated", "nestedData" : {"nestedKey2":"nestedValue2"}}, {"id":"3", "data":"testdata3", "data2":"commonUpdated", "nestedData" : {"nestedKey3":"nestedValue3"}}]' http://<%=ip%>:8080<%=context%>/rest/data/Item?space=<%=spaceName%></p></li>
			<p>see that data2 field is updated: <a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=data2='commonUpdated'&space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=data2='commonUpdated'&space=<%=spaceName%></a></p>
		</ul>
		</li>
		<li> single nested update
		<ul>
			<li><p>curl -XPUT -H "Content-Type: application/json" -d '{"id":"1", "data":"testdata", "data2":"commonUpdated", "nestedData" : {"nestedKey1":"nestedValue1Updated"}}' http://<%=ip%>:8080<%=context%>/rest/data/Item?space=<%=spaceName%></p></li>
			<p>see that Item1 nested field is updated: <a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/1?space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/1?space=<%=spaceName%></a></p>
		</ul>
		</li>

		<li> takeMultiple (the url is encoded, the query is "id=1 or id=2"):
		<ul>
			<li><p>curl -XDELETE "http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=id=%271%27%20or%20id=%272%27&space=<%=spaceName%>"</p></li>
			<p>see that only Item3 remains: <a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=id='1' or id='2' or id='3'&space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=id='1' or id='2' or id='3'&space=<%=spaceName%></a></p>
		</ul>
		</li>

		<li> takeById
		<ul>
			<li><p>curl -XDELETE "http://<%=ip%>:8080<%=context%>/rest/data/Item/3?space=<%=spaceName%>"</p></li>
			<p>see that item3 does not exist,response status 404 and the json {"error":"object not found"} <a href="http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=id='1' or id='2' or id='3'&space=<%=spaceName%>">http://<%=ip%>:8080<%=context%>/rest/data/Item/_criteria?q=id='1' or id='2' or id='3'&space=<%=spaceName%></a></p>
		</ul>

	</ul>
</body>
</html>