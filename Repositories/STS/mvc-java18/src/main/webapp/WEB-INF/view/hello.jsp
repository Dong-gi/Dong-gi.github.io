<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<html lang="ko">

<head>
    <meta charset="UTF-8">
</head>

<body>
    <%
        Object message = request.getAttribute("message");
        if(message == null)
            message = "(Default message)안녕 세상!";
    %>
    <%= message %>
</body>

</html>