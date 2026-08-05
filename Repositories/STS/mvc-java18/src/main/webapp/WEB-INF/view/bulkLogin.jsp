<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<html lang="ko">

<head>
    <meta charset="UTF-8">
</head>

<body>
    <form method="post">
        email1 : <input type="email" name="loginRequests[0].email" value="test1@test.com" /><br>
        password1 : <input type="password" name="loginRequests[0].password" value="password" /><br>
        email2 : <input type="email" name="loginRequests[1].email" value="test2@test.com" /><br>
        password2 : <input type="password" name="loginRequests[1].password" value="password" /><br>
        email3 : <input type="email" name="loginRequests[2].email" value="test3@test.com" /><br>
        password3 : <input type="password" name="loginRequests[2].password" value="password" />
        <input type="submit" />
    </form>
</body>

</html>