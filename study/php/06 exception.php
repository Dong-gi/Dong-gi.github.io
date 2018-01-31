<?php
header('Content-Type: text/html; charset=utf-8');
// $host = $_SERVER['HTTP_HOST'];
setlocale(LC_TIME, "kr_KR.utf8");
date_default_timezone_set('Asia/Seoul');

?>
<html>
    <head>
        <title>06 exception</title>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
    </head>
    <body>
        <div id="main">
            <div id="content"> 
<?php
// throw new Exception('message', code)
try
{
	throw new Exception("Error", 88);
}
// catch(typehint exception)
catch(Exception $e)
{
	echo $e;
}
?>
			</div>
            <div id="footer">
            </div>
        </div>
    </body>
</html>