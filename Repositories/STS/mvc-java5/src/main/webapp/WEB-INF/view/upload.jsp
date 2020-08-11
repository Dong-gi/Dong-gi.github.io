<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>

<!DOCTYPE html>
<html>
<head>
	<meta charset="UTF-8">
	<title>Upload</title>
</head>
<body>
    <form method="post" enctype="multipart/form-data">
        <input type="file" name="files" multiple="multiple"/>
        <input type="submit"/>
    </form>
</body>
</html>