<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page isELIgnored="false" %>
<%@ page import="java.util.Date" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>

<html lang="ko">
    <head>
        <meta charset="utf-8">
    </head>

    <body>
        <spring:message code="is.empty.email"/><br>
        <spring:message code="hello" arguments="Donggi, ${now.toString()}" htmlEscape="true"/><br>
        <spring:message code="not.exists.code" text="기본 메시지"/>
    </body>
</html>