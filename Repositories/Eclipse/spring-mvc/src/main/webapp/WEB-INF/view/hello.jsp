<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
<body>
<%
Object message = request.getAttribute("message");
if(message == null)
    message = "안녕 세상!";
%>
<%= message %>
</body>
</html>