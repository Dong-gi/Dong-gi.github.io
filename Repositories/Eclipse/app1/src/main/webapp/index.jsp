<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>

<html lnag="ko">
    <head>
        <meta charset="utf-8">
        <title>app1 index</title>
    </head>

    <body>
        <ol></ol>
    </body>
    
    <script>
    let pages = [
    	['./hello1', '튜토리얼 Hello1 서블릿'],
    	['./lifeCycle', '서블릿 생애주기'],
    	['./hello2', '@WebServlet Hello2 서블릿'],
    	['./hello3', '@WebServlet Hello3 서블릿'],
    	['./hello4', 'MySQL 테스트 서블릿'],
    	['./callOther/refreshHeader', '다른 페이지 이동/호출 : HTTP 응답 헤더에 Refresh 설정'],
    	['./callOther/refreshMeta', '다른 페이지 이동/호출 : HTML <meta> 태그에 Refresh 설정'],
    	['./callOther/redirect', '다른 페이지 이동/호출 : 서블릿 Redirect 호출'],
    	['./callOther/forward', '다른 페이지 이동/호출 : 서블릿 Forward 호출'],
    	['./callOther/include', '다른 페이지 이동/호출 : 서블릿 include 호출'],
    	['./', '서블릿'],
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