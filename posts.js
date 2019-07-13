const posts = {
    "list": [
        { "filename": "posts/algorithm/koreatech.html", "category": "Algorithm", "title": "jduge.koreatech.ac.kr" },

        { "filename": "posts/book/001.html", "category": "Book", "title": "1만 시간의 재발견" },
        { "filename": "posts/book/002.html", "category": "Book", "title": "마음의 탄생" },
        { "filename": "posts/book/003.html", "category": "Book", "title": "생각하는 삶을 위한 철학의 역사" },
        { "filename": "posts/book/004.html", "category": "Book", "title": "쇼펜하우어, 돌이 별이 되는 철학" },
        { "filename": "posts/book/005.html", "category": "Book", "title": "인생의 모든 의미" },
        { "filename": "posts/book/006.html", "category": "Book", "title": "지식의 착각" },
        { "filename": "posts/book/007.html", "category": "Book", "title": "키르케고르 실존 극장" },

        { "filename": "posts/db/concept.html", "category": "DB", "title": "기본 개념" },
        { "filename": "posts/db/mysql.html", "category": "DB", "title": "MySQL" },
        { "filename": "posts/db/psql_admin.html", "category": "DB", "title": "PostgreSQL 서버 관리" },
        { "filename": "posts/db/psql_programming.html", "category": "DB", "title": "PostgreSQL 서버 프로그래밍" },
        { "filename": "posts/db/psql_sql.html", "category": "DB", "title": "PostgreSQL SQL 언어" },
        { "filename": "posts/db/psql_tutorial.html", "category": "DB", "title": "PostgreSQL 시작하기" },
        { "filename": "posts/db/psql_to_sqlite.html", "category": "DB", "title": "PostgreSQL → SQLite3 마이그레이션" },

        { "filename": "posts/front/freemarker/built_in.html", "category": "Programming/Front/FreeMarker", "title": "Built-in 목록 2.3.28" },
        { "filename": "posts/front/freemarker/programming_guide.html", "category": "Programming/Front/FreeMarker", "title": "프로그래밍 가이드 2.3.28" },
        { "filename": "posts/front/freemarker/template_guide.html", "category": "Programming/Front/FreeMarker", "title": "템플릿 작성 가이드 2.3.28" },
        { "filename": "posts/front/freemarker/xml_guide.html", "category": "Programming/Front/FreeMarker", "title": "XML 처리 가이드 2.3.28" },
        { "filename": "posts/front/css.html", "category": "Programming/Front/CSS", "title": "CSS", "top": false },
        { "filename": "posts/front/html.html", "category": "Programming/Front/HTML", "title": "HTML", "top": false },
        { "filename": "posts/front/http.html", "category": "Programming/Front/HTTP", "title": "HTTP 1.1", "top": true },

        { "filename": "posts/1810/452.html", "category": "Programming/C/Basic", "title": "문법", "top": true },
        { "filename": "posts/1810/472.html", "category": "Programming/C/Library", "title": "라이브러리", "top": true },
        { "filename": "posts/1810/473.html", "category": "Programming/C++/Basic", "title": "문법", "top": true },
        { "filename": "posts/1810/474.html", "category": "Programming/C++/Library", "title": "라이브러리", "top": true },
        { "filename": "posts/1812/489.html", "category": "Programming/C#.NET/Basic", "title": "C# 기본", "top": true },
        { "filename": "posts/1812/491.html", "category": "Programming/C#.NET/Library", "title": "C# 라이브러리", "top": true },
        { "filename": "posts/1901/493.html", "category": "Programming/C#.NET/WPF", "title": "WPF", "top": true },

        { "filename": "posts/java/basic.html", "category": "Programming/Java/Tutorial", "title": "Java 시작하기" },
        { "filename": "posts/java/javafx.html", "category": "Programming/Java/Tutorial", "title": "JavaFX" },
        { "filename": "posts/java/jni.html", "category": "Programming/Java/Tutorial", "title": "Java Native Interface" },
        { "filename": "posts/java/oop.html", "category": "Programming/Java/Tutorial", "title": "Java 객체지향" },
        { "filename": "posts/java/version.html", "category": "Programming/Java/Tutorial", "title": "Java 버전별 추가사항" },

        { "filename": "posts/java/java.io.html", "category": "Programming/Java/java.base", "title": "java.io" },
        { "filename": "posts/java/java.lang.html", "category": "Programming/Java/java.base", "title": "java.lang" },
        { "filename": "posts/java/java.lang.annotation.html", "category": "Programming/Java/java.base", "title": "java.lang.annotation" },
        { "filename": "posts/java/java.lang.module.html", "category": "Programming/Java/java.base", "title": "java.lang.module" },
        { "filename": "posts/java/java.lang.reflect.html", "category": "Programming/Java/java.base", "title": "java.lang.reflect" },
        { "filename": "posts/java/java.math.html", "category": "Programming/Java/java.base", "title": "java.math" },
        { "filename": "posts/java/java.net.html", "category": "Programming/Java/java.base", "title": "java.net" },
        { "filename": "posts/java/java.nio.html", "category": "Programming/Java/java.base", "title": "java.nio" },
        { "filename": "posts/java/java.nio.channels.html", "category": "Programming/Java/java.base", "title": "java.nio.channels" },
        { "filename": "posts/java/java.nio.charset.html", "category": "Programming/Java/java.base", "title": "java.nio.charset" },
        { "filename": "posts/java/java.nio.file.html", "category": "Programming/Java/java.base", "title": "java.nio.file" },
        { "filename": "posts/java/java.text.html", "category": "Programming/Java/java.base", "title": "java.text" },
        { "filename": "posts/java/java.time.html", "category": "Programming/Java/java.base", "title": "java.time" },
        { "filename": "posts/java/java.time.format.html", "category": "Programming/Java/java.base", "title": "java.time.format" },
        { "filename": "posts/java/java.util.html", "category": "Programming/Java/java.base", "title": "java.util", "top": true },
        { "filename": "posts/java/java.util.concurrent.html", "category": "Programming/Java/java.base", "title": "java.util.concurrent", "top": true },
        { "filename": "posts/java/java.util.concurrent.atomic.html", "category": "Programming/Java/java.base", "title": "java.util.concurrent.atomic" },
        { "filename": "posts/java/java.util.function.html", "category": "Programming/Java/java.base", "title": "java.util.function" },
        { "filename": "posts/java/java.util.regex.html", "category": "Programming/Java/java.base", "title": "java.util.regex" },
        { "filename": "posts/java/java.util.stream.html", "category": "Programming/Java/java.base", "title": "java.util.stream" },
        { "filename": "posts/java/java.util.zip.html", "category": "Programming/Java/java.base", "title": "java.util.zip" },

        { "filename": "posts/1810/458.html", "category": "Programming/Java/Servlet", "title": "필터와 래퍼", "top": true },
        { "filename": "posts/1810/457.html", "category": "Programming/Java/Servlet", "title": "컨텍스트 초기화 매개변수", "top": true },
        { "filename": "posts/1810/456.html", "category": "Programming/Java/Servlet", "title": "다른 페이지 이동/호출", "top": true },
        { "filename": "posts/1810/455.html", "category": "Programming/Java/Servlet", "title": "MySQL 테스트", "top": true },
        { "filename": "posts/1810/454.html", "category": "Programming/Java/Servlet", "title": "web.xml 대신 애너테이션으로 서블릿 기술하기", "top": true },
        { "filename": "posts/1810/453.html", "category": "Programming/Java/Servlet", "title": "서블릿 생애주기", "top": true },
        { "filename": "posts/1809/446.html", "category": "Programming/Java/Servlet", "title": "JSP&Servlet", "top": true },
        
        { "filename": "posts/1810/461.html", "category": "Programming/JavaScript/Basic", "title": "Hello World", "top": true },
        { "filename": "posts/1810/462.html", "category": "Programming/JavaScript/Basic", "title": "자료형, 변수, 식별자", "top": true },
        { "filename": "posts/1810/463.html", "category": "Programming/JavaScript/Basic", "title": "Syntax", "top": true },
        { "filename": "posts/1810/464.html", "category": "Programming/JavaScript/Basic", "title": "연산자", "top": true },
        { "filename": "posts/1810/465.html", "category": "Programming/JavaScript/Basic", "title": "함수", "top": true },
        { "filename": "posts/1810/466.html", "category": "Programming/JavaScript/Basic", "title": "Closure", "top": true },
        { "filename": "posts/1810/467.html", "category": "Programming/JavaScript/Basic", "title": "Class", "top": true },
        { "filename": "posts/1810/468.html", "category": "Programming/JavaScript/Basic", "title": "Iteration Protocol", "top": true },
        { "filename": "posts/1810/469.html", "category": "Programming/JavaScript/Basic", "title": "Generator", "top": true },
        { "filename": "posts/1810/470.html", "category": "Programming/JavaScript/Basic", "title": "비동기 처리", "top": true },
        { "filename": "posts/1810/478.html", "category": "Programming/JavaScript/Browser", "title": "HelloWorld", "top": true },
        { "filename": "posts/1811/479.html", "category": "Programming/JavaScript/Browser", "title": "BOM(Browser Object Model)", "top": true },
        { "filename": "posts/1811/480.html", "category": "Programming/JavaScript/Browser", "title": "DOM(Document Object Model)", "top": true },
        { "filename": "posts/1811/481.html", "category": "Programming/JavaScript/Browser", "title": "Audio Player", "top": true },
        { "filename": "posts/1811/482.html", "category": "Programming/JavaScript/Browser", "title": "Form", "top": true },
        { "filename": "posts/1811/483.html", "category": "Programming/JavaScript/Basic", "title": "정규표현식", "top": true },
        { "filename": "posts/1811/484.html", "category": "Programming/JavaScript/Browser", "title": "CSS 선택자", "top": true },
        { "filename": "posts/1811/485.html", "category": "Programming/JavaScript/Browser", "title": "브라우저 저장소", "top": true },
        { "filename": "posts/1811/486.html", "category": "Programming/JavaScript/Browser", "title": "jQuery Basic", "top": true },
        { "filename": "posts/1812/487.html", "category": "Programming/JavaScript/Browser", "title": "jQuery DOM", "top": true },
        { "filename": "posts/1812/488.html", "category": "Programming/JavaScript/Browser", "title": "jQuery Event", "top": true },
        { "filename": "posts/1901/492.html", "category": "Programming/JavaScript/Basic", "title": "Etc", "top": true },        

        { "filename": "posts/1904/497.html", "category": "Programming/Python/Basic", "title": "Python 기본", "top": true },
        { "filename": "posts/1904/1904252127.html", "category": "Programming/Python/Document", "title": "Built-in Functions", "top": true },
        { "filename": "posts/1904/1904252130.html", "category": "Programming/Python/Document", "title": "Built-in Constants", "top": true },
        { "filename": "posts/1904/1904252131.html", "category": "Programming/Python/Document", "title": "Built-in Types", "top": true },
        { "filename": "posts/1904/1904252133.html", "category": "Programming/Python/Document", "title": "Built-in Exceptions", "top": true },
        { "filename": "posts/1904/1904252134.html", "category": "Programming/Python/Document", "title": "Text Processing Services", "top": true },
        { "filename": "posts/1904/1904252135.html", "category": "Programming/Python/Document", "title": "Binary Data Services", "top": true },
        { "filename": "posts/1904/1904252136.html", "category": "Programming/Python/Document", "title": "Data Types", "top": true },

        { "filename": "posts/tip/docker.html", "category": "Tip", "title": "Docker" },
        { "filename": "posts/tip/giregi.html", "category": "Tip", "title": "기레기 고소 방법" },
        { "filename": "posts/tip/hangul.html", "category": "Tip", "title": "한글 유니코드" },
        { "filename": "posts/tip/linux.html", "category": "Tip", "title": "Linux" },
        { "filename": "posts/tip/macro.html", "category": "Tip", "title": "macro" },
        { "filename": "posts/tip/playstore.html", "category": "Tip", "title": "Google Play Store" },
        { "filename": "posts/tip/psql.html", "category": "Tip", "title": "psql" },
        { "filename": "posts/tip/regex.html", "category": "Tip", "title": "정규표현식" },
        { "filename": "posts/tip/vi.html", "category": "Tip", "title": "vi" },
        { "filename": "posts/tip/vs_code.html", "category": "Tip", "title": "VS Code" },
        { "filename": "posts/tip/windows.html", "category": "Tip", "title": "Windows" },
    ],
    "tree": {},
    "contents": [],
    "visible": [],
    "codes": {},
};

/*
lan 속성 : nohighlight, text, java, javascript, cpp, matlab, xml, css, ruby, sql, bash, shell, php, go, python, cs
displayRange="[]"

<button type="button" class="btn btn-primary btn-sm btn-code" path="Java/JavaSE/src/concurrent/.java" lan="java">예시 코드 »</button>
        
<a role="button" target="_blank" class="btn btn-info btn-sm" href=""></a>
<a role="button" target="#" class="btn btn-info btn-sm" href="javascript:;"></a>
<img class="rounded mx-auto d-block" alt="" src="imgs/">

<details>
    <summary></summary>
    <ol>

    </ol>
</details>

<hr>

<details>
    <summary>Method Detail</summary>
    <ol>

    </ol>
</details>
 */