<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>

<!DOCTYPE html>
<html>
<head>
	<meta charset="UTF-8">
	<title>Download</title>
</head>
<body>
	<h1>선택한 파일(들) 그대로 다운로드</h1>
    <form action="download" method="post" enctype="multipart/form-data">
        <input type="file" name="files" multiple="multiple"/>
        <input type="submit" value="download"/>
    </form>

	<h1>선택한 파일(들) zip으로 다운로드</h1>
    <form action="downloadZip" method="post" enctype="multipart/form-data">
        <input type="file" name="files" multiple="multiple"/>
        <input type="submit" value="downloadZip"/>
    </form>
</body>
</html>