<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<title>Insert title here</title>
</head>
<body>
    <form action="download" method="post" enctype="multipart/form-data">
        <input type="file" name="files" multiple="multiple"/>
        <input type="submit" value="download"/>
    </form>
    
    <form action="downloadZip" method="post" enctype="multipart/form-data">
        <input type="file" name="files" multiple="multiple"/>
        <input type="submit" value="downloadZip"/>
    </form>
</body>
</html>