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
    	['./hello.do', 'HelloController::hello'],
    	['./common-hello.do', 'HelloController::commonHello'],
    	['./login.do', 'LoginController::login'],
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