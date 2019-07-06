const posts = {
    "list": [
        { "filename": "posts/book/001.html", "category": "Book", "title": "1만 시간의 재발견", "top": false },
        { "filename": "posts/book/002.html", "category": "Book", "title": "마음의 탄생", "top": false },
        { "filename": "posts/book/003.html", "category": "Book", "title": "생각하는 삶을 위한 철학의 역사", "top": false },
        { "filename": "posts/book/004.html", "category": "Book", "title": "쇼펜하우어, 돌이 별이 되는 철학", "top": false },
        { "filename": "posts/book/005.html", "category": "Book", "title": "인생의 모든 의미", "top": false },
        { "filename": "posts/book/006.html", "category": "Book", "title": "지식의 착각", "top": false },
        { "filename": "posts/book/007.html", "category": "Book", "title": "키르케고르 실존 극장", "top": false },

        { "filename": "posts/tip/docker.html", "category": "Tip", "title": "Docker", "top": false },
        { "filename": "posts/tip/giregi.html", "category": "Tip", "title": "기레기 고소 방법", "top": false },
        { "filename": "posts/tip/hangul.html", "category": "Tip", "title": "한글 유니코드", "top": false },
        { "filename": "posts/tip/linux.html", "category": "Tip", "title": "Linux", "top": false },
        { "filename": "posts/tip/playstore.html", "category": "Tip", "title": "Google Play Store", "top": false },
        { "filename": "posts/tip/psql.html", "category": "Tip", "title": "psql", "top": false },
        { "filename": "posts/tip/vi.html", "category": "Tip", "title": "vi", "top": false },
        { "filename": "posts/tip/vs_code.html", "category": "Tip", "title": "VS Code", "top": false },
        { "filename": "posts/tip/windows.html", "category": "Tip", "title": "Windows", "top": false },

        { "filename": "posts/java/basic.html", "category": "Programming/Java/Tutorial", "title": "Java 시작하기", "top": false },
        { "filename": "posts/java/javafx.html", "category": "Programming/Java/Tutorial", "title": "JavaFX", "top": false },
        { "filename": "posts/java/jni.html", "category": "Programming/Java/Tutorial", "title": "Java Native Interface", "top": false },
        { "filename": "posts/java/oop.html", "category": "Programming/Java/Tutorial", "title": "Java 객체지향", "top": false },
        { "filename": "posts/java/version.html", "category": "Programming/Java/Tutorial", "title": "Java 버전별 추가사항", "top": false },

        { "filename": "posts/java/java.io.html", "category": "Programming/Java/java.base", "title": "java.io", "top": false },
        { "filename": "posts/java/java.lang.html", "category": "Programming/Java/java.base", "title": "java.lang", "top": false },
        { "filename": "posts/java/java.lang.annotation.html", "category": "Programming/Java/java.base", "title": "java.lang.annotation", "top": false },
        { "filename": "posts/java/java.lang.module.html", "category": "Programming/Java/java.base", "title": "java.lang.module", "top": false },
        { "filename": "posts/java/java.lang.reflect.html", "category": "Programming/Java/java.base", "title": "java.lang.reflect", "top": true },
        { "filename": "posts/java/java.math.html", "category": "Programming/Java/java.base", "title": "java.math" },
        { "filename": "posts/java/java.net.html", "category": "Programming/Java/java.base", "title": "java.net" },
        { "filename": "posts/java/java.nio.html", "category": "Programming/Java/java.base", "title": "java.nio" },
        { "filename": "posts/java/java.nio.channels.html", "category": "Programming/Java/java.base", "title": "java.nio.channels" },
        { "filename": "posts/java/java.nio.charset.html", "category": "Programming/Java/java.base", "title": "java.nio.charset" },
        { "filename": "posts/java/java.nio.file.html", "category": "Programming/Java/java.base", "title": "java.nio.file" },
        { "filename": "posts/java/java.text.html", "category": "Programming/Java/java.base", "title": "java.text" },
        { "filename": "posts/java/java.time.html", "category": "Programming/Java/java.base", "title": "java.time" },
        { "filename": "posts/java/java.time.format.html", "category": "Programming/Java/java.base", "title": "java.time.format" },
        { "filename": "posts/java/java.util.html", "category": "Programming/Java/java.base", "title": "java.util" },
        { "filename": "posts/java/java.util.concurrent.html", "category": "Programming/Java/java.base", "title": "java.util.concurrent" },
        { "filename": "posts/java/java.util.concurrent.atomic.html", "category": "Programming/Java/java.base", "title": "java.util.concurrent.atomic" },
        { "filename": "posts/java/java.util.function.html", "category": "Programming/Java/java.base", "title": "java.util.function" },
        { "filename": "posts/java/java.util.regex.html", "category": "Programming/Java/java.base", "title": "java.util.regex" },
        { "filename": "posts/java/java.util.stream.html", "category": "Programming/Java/java.base", "title": "java.util.stream" },
        { "filename": "posts/java/java.util.zip.html", "category": "Programming/Java/java.base", "title": "java.util.zip" },

        { "filename": "posts/1810/458.html", "category": "Programming/Java/Servlet", "title": "필터와 래퍼" },
        { "filename": "posts/1810/457.html", "category": "Programming/Java/Servlet", "title": "컨텍스트 초기화 매개변수" },
        { "filename": "posts/1810/456.html", "category": "Programming/Java/Servlet", "title": "다른 페이지 이동/호출" },
        { "filename": "posts/1810/455.html", "category": "Programming/Java/Servlet", "title": "MySQL 테스트" },
        { "filename": "posts/1810/454.html", "category": "Programming/Java/Servlet", "title": "web.xml 대신 애너테이션으로 서블릿 기술하기" },
        { "filename": "posts/1810/453.html", "category": "Programming/Java/Servlet", "title": "서블릿 생애주기" },
        { "filename": "posts/1809/446.html", "category": "Programming/Java/Servlet", "title": "JSP&Servlet" },
        
        { "filename": "posts/freemarker/template_guide.html", "category": "Programming/Java/FreeMarker/매뉴얼", "title": "템플릿 작성 가이드 2.3.28" },
        { "filename": "posts/freemarker/programming_guide.html", "category": "Programming/Java/FreeMarker/매뉴얼", "title": "프로그래밍 가이드 2.3.28" },
        { "filename": "posts/freemarker/xml_guide.html", "category": "Programming/Java/FreeMarker/매뉴얼", "title": "XML 처리 가이드 2.3.28" },
        { "filename": "posts/freemarker/built_in.html", "category": "Programming/Java/FreeMarker/매뉴얼", "title": "Built-in 목록 2.3.28" },
        

        { "filename": "posts/1809/372.html", "category": "Programming/CSS", "title": "CSS" },
        { "filename": "posts/1809/373.html", "category": "Programming/HTML", "title": "HTML" },
        { "filename": "posts/1904/1904252139.html", "category": "Programming/HTTP", "title": "HTTP 1.1" },
        

        { "filename": "posts/1809/374.html", "category": "Algorithm", "title": "jduge.koreatech.ac.kr" },

        { "filename": "posts/1810/461.html", "category": "Programming/JavaScript/Basic", "title": "Hello World" },
        { "filename": "posts/1810/462.html", "category": "Programming/JavaScript/Basic", "title": "자료형, 변수, 식별자" },
        { "filename": "posts/1810/463.html", "category": "Programming/JavaScript/Basic", "title": "Syntax" },
        { "filename": "posts/1810/464.html", "category": "Programming/JavaScript/Basic", "title": "연산자" },
        { "filename": "posts/1810/465.html", "category": "Programming/JavaScript/Basic", "title": "함수" },
        { "filename": "posts/1810/466.html", "category": "Programming/JavaScript/Basic", "title": "Closure" },
        { "filename": "posts/1810/467.html", "category": "Programming/JavaScript/Basic", "title": "Class" },
        { "filename": "posts/1810/468.html", "category": "Programming/JavaScript/Basic", "title": "Iteration Protocol" },
        { "filename": "posts/1810/469.html", "category": "Programming/JavaScript/Basic", "title": "Generator" },
        { "filename": "posts/1810/470.html", "category": "Programming/JavaScript/Basic", "title": "비동기 처리" },
        { "filename": "posts/1810/478.html", "category": "Programming/JavaScript/Browser", "title": "HelloWorld" },
        { "filename": "posts/1811/479.html", "category": "Programming/JavaScript/Browser", "title": "BOM(Browser Object Model)" },
        { "filename": "posts/1811/480.html", "category": "Programming/JavaScript/Browser", "title": "DOM(Document Object Model)" },
        { "filename": "posts/1811/481.html", "category": "Programming/JavaScript/Browser", "title": "Audio Player" },
        { "filename": "posts/1811/482.html", "category": "Programming/JavaScript/Browser", "title": "Form" },
        { "filename": "posts/1811/483.html", "category": "Programming/JavaScript/Basic", "title": "정규표현식" },
        { "filename": "posts/1811/484.html", "category": "Programming/JavaScript/Browser", "title": "CSS 선택자" },
        { "filename": "posts/1811/485.html", "category": "Programming/JavaScript/Browser", "title": "브라우저 저장소" },
        { "filename": "posts/1811/486.html", "category": "Programming/JavaScript/Browser", "title": "jQuery Basic" },
        { "filename": "posts/1812/487.html", "category": "Programming/JavaScript/Browser", "title": "jQuery DOM" },
        { "filename": "posts/1812/488.html", "category": "Programming/JavaScript/Browser", "title": "jQuery Event" },
        { "filename": "posts/1901/492.html", "category": "Programming/JavaScript/Basic", "title": "Etc" },
        
        
        { "filename": "posts/1810/452.html", "category": "Programming/C/Basic", "title": "문법" },
        { "filename": "posts/1810/472.html", "category": "Programming/C/Library", "title": "라이브러리" },
        { "filename": "posts/1810/473.html", "category": "Programming/C++/Basic", "title": "문법" },
        { "filename": "posts/1810/474.html", "category": "Programming/C++/Library", "title": "라이브러리" },
        { "filename": "posts/1812/489.html", "category": "Programming/C#.NET/Basic", "title": "C# 기본" },
        { "filename": "posts/1812/491.html", "category": "Programming/C#.NET/Library", "title": "C# 라이브러리" },
        { "filename": "posts/1901/493.html", "category": "Programming/C#.NET/WPF", "title": "WPF" },
        

        { "filename": "posts/1904/497.html", "category": "Programming/Python/Basic", "title": "Python 기본" },
        { "filename": "posts/1904/1904252127.html", "category": "Programming/Python/Document", "title": "Built-in Functions" },
        { "filename": "posts/1904/1904252130.html", "category": "Programming/Python/Document", "title": "Built-in Constants" },
        { "filename": "posts/1904/1904252131.html", "category": "Programming/Python/Document", "title": "Built-in Types" },
        { "filename": "posts/1904/1904252133.html", "category": "Programming/Python/Document", "title": "Built-in Exceptions" },
        { "filename": "posts/1904/1904252134.html", "category": "Programming/Python/Document", "title": "Text Processing Services" },
        { "filename": "posts/1904/1904252135.html", "category": "Programming/Python/Document", "title": "Binary Data Services" },
        { "filename": "posts/1904/1904252136.html", "category": "Programming/Python/Document", "title": "Data Types" },
        

        { "filename": "posts/db/concept.html", "category": "DB", "title": "기본 개념", "top": false },
        { "filename": "posts/db/psql_tutorial.html", "category": "DB", "title": "PostgreSQL 시작하기", "top": false },
        { "filename": "posts/db/psql_sql.html", "category": "DB", "title": "PostgreSQL SQL 언어", "top": true },
        { "filename": "posts/db/psql_to_sqlite.html", "category": "DB", "title": "PostgreSQL → SQLite3 마이그레이션", "top": false },
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