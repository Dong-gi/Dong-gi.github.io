<%@page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<html lnag="ko">
    <head>
        <meta charset="utf-8">
        <title>Sprint MVC index</title>
    </head>

    <body>
        <ol></ol>
    </body>
    
    <script>
    let pages = [
    	['./app/hello/', 'HelloController::hello'],
    	['./app/hello/simpleHello', 'HelloController::simpleHello'],
    	['./app/hello/commonHello', 'HelloController::commonHello'],
    	['./app/hello/Donggi/a/b/c/Hello', 'HelloController::restHello'],
    	['./app/hello/fail', 'HelloController::failHello'],
    	['./app/hello/fail2', 'HelloController::failHello2'],
    	['./app/login', 'LoginController::login'],
    	['./app/bulkLogin', 'BulkLoginController::login'],
    	['./app/file/upload', 'FileController::upload'],
    	['./app/file/download', 'FileController::download'],
    	['./app/etc/date', 'EtcController::date'],
    	['./app/etc/json', 'EtcController::json'],
    ];

    for(let page of pages) {
    	let a = document.createElement('a');
    	a.setAttribute('target', '_blank');
    	a.setAttribute('href', page[0]);
    	a.textContent = page[1];
    	
    	let li = document.createElement('li');
    	li.appendChild(a);
        
    	document.getElementsByTagName('ol')[0].appendChild(li);
    }
    </script>
</html>