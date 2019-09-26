const posts = {
    list: [
        { filename: "posts/algorithm/book01.html", category: "Algorithm", title: "『알고리즘 도감』" },
        { filename: "posts/algorithm/koreatech.html", category: "Algorithm", title: "jduge.koreatech.ac.kr" },

        { filename: "posts/book/001.html", category: "Book", title: "1만 시간의 재발견" },
        { filename: "posts/book/002.html", category: "Book", title: "마음의 탄생" },
        { filename: "posts/book/003.html", category: "Book", title: "생각하는 삶을 위한 철학의 역사" },
        { filename: "posts/book/004.html", category: "Book", title: "쇼펜하우어, 돌이 별이 되는 철학" },
        { filename: "posts/book/005.html", category: "Book", title: "인생의 모든 의미" },
        { filename: "posts/book/006.html", category: "Book", title: "지식의 착각" },
        { filename: "posts/book/007.html", category: "Book", title: "키르케고르 실존 극장" },
        { filename: "posts/book/008.html", category: "Book", title: "Gamification by Design" },

        { filename: "posts/career/at_home_at_20190724.html", category: "Career", title: "JavaScript - https://www.steamgifts.com/ 스크립트" },
        { filename: "posts/career/at_sesisoft_from_201806.html", category: "Career", title: "C#.NET, WPF - 데이터 작업 툴" },
        { filename: "posts/career/at_sesisoft_at_20190715.html", category: "Career", title: "JavaScript - IGA Works 푸시 등록 절차 개선" },
        { filename: "posts/career/at_sesisoft_at_20190626.html", category: "Career", title: "JavaScript - 자연스러운 테이블 정렬" },
        { filename: "posts/career/at_sesisoft_at_20190808.html", category: "Career", title: "JavaScript - 구글 스프레드시트 스크립트" },

        { filename: "posts/db/concept.html", category: "Programming/DB", title: "기본 개념" },
        { filename: "posts/db/mysql.html", category: "Programming/DB", title: "MySQL" },
        { filename: "posts/db/psql_admin.html", category: "Programming/DB", title: "PostgreSQL 서버 관리" },
        { filename: "posts/db/psql_programming.html", category: "Programming/DB", title: "PostgreSQL 서버 프로그래밍" },
        { filename: "posts/db/psql_sql.html", category: "Programming/DB", title: "PostgreSQL SQL 언어" },
        { filename: "posts/db/psql_tutorial.html", category: "Programming/DB", title: "PostgreSQL 시작하기" },
        { filename: "posts/db/psql_to_sqlite.html", category: "Programming/DB", title: "PostgreSQL → SQLite3 마이그레이션" },

        { filename: "posts/front/css.html", category: "Programming/Front", title: "CSS" },
        { filename: "posts/front/html.html", category: "Programming/Front", title: "HTML" },
        { filename: "posts/front/http.html", category: "Programming/Front", title: "HTTP 1.1" },
        { filename: "posts/front/freemarker/built_in.html", category: "Programming/Front/FreeMarker", title: "Built-in 목록 2.3.28" },
        { filename: "posts/front/freemarker/programming_guide.html", category: "Programming/Front/FreeMarker", title: "프로그래밍 가이드 2.3.28" },
        { filename: "posts/front/freemarker/template_guide.html", category: "Programming/Front/FreeMarker", title: "템플릿 작성 가이드 2.3.28" },
        { filename: "posts/front/freemarker/xml_guide.html", category: "Programming/Front/FreeMarker", title: "XML 처리 가이드 2.3.28" },

        { filename: "posts/c/c_basic.html", category: "Programming/C", title: "C 시작하기" },
        { filename: "posts/c/c_library.html", category: "Programming/C", title: "C 라이브러리" },
        { filename: "posts/c/cpp_basic.html", category: "Programming/C++", title: "C++ 시작하기" },
        { filename: "posts/c/cpp_library.html", category: "Programming/C++", title: "C++ 라이브러리" },
        { filename: "posts/c/csharp_basic.html", category: "Programming/C#.NET", title: "C# 시작하기" },
        { filename: "posts/c/csharp_library.html", category: "Programming/C#.NET", title: "C# 라이브러리" },
        { filename: "posts/c/wpf_basic.html", category: "Programming/C#.NET", title: "WPF 시작하기" },

        { filename: "posts/java/basic.html", category: "Programming/Java", title: "Java 시작하기" },
        { filename: "posts/java/effective_java.html", category: "Programming/Java", title: "『Effective Java』" },
        { filename: "posts/java/java_ee.html", category: "Programming/Java", title: "JavaEE", top: true },
        { filename: "posts/java/javafx.html", category: "Programming/Java", title: "JavaFX" },
        { filename: "posts/java/jni.html", category: "Programming/Java", title: "Java Native Interface" },
        { filename: "posts/java/oop.html", category: "Programming/Java", title: "Java 객체지향" },
        { filename: "posts/java/version.html", category: "Programming/Java", title: "Java 버전별 추가사항" },
        
        { filename: "posts/java/apache.commons.collections.html", category: "Programming/Java/apache.commons", title: "Apache Commons Collections 4.4" },
        { filename: "posts/java/apache.commons.lang.html", category: "Programming/Java/apache.commons", title: "Apache Commons Lang 3.9" },
        { filename: "posts/java/apache.commons.math.html", category: "Programming/Java/apache.commons", title: "Apache Commons Math 3.6.1", top: true },
        { filename: "posts/java/apache.commons.rng.html", category: "Programming/Java/apache.commons", title: "Apache Commons RNG Core 1.2" },
        { filename: "posts/java/guava.html", category: "Programming/Java/google", title: "Guava 18.0", top: true },
        { filename: "posts/java/java.io.html", category: "Programming/Java/java.base", title: "java.io 12 Since 1.0" },
        { filename: "posts/java/java.lang.html", category: "Programming/Java/java.base", title: "java.lang 12 Since 1.0" },
        { filename: "posts/java/java.lang.annotation.html", category: "Programming/Java/java.base", title: "java.lang.annotation 12 Since 1.5" },
        { filename: "posts/java/java.lang.module.html", category: "Programming/Java/java.base", title: "java.lang.module 12 Since 9" },
        { filename: "posts/java/java.lang.ref.html", category: "Programming/Java/java.base", title: "java.lang.ref 12 Since 1.2" },
        { filename: "posts/java/java.lang.reflect.html", category: "Programming/Java/java.base", title: "java.lang.reflect 12 Since 1.1" },
        { filename: "posts/java/java.math.html", category: "Programming/Java/java.base", title: "java.math 12 Since 1.1" },
        { filename: "posts/java/java.net.html", category: "Programming/Java/java.base", title: "java.net 12 Since 1.0" },
        { filename: "posts/java/java.nio.html", category: "Programming/Java/java.base", title: "java.nio 12 Since 1.4" },
        { filename: "posts/java/java.nio.channels.html", category: "Programming/Java/java.base", title: "java.nio.channels 12 Since 1.4" },
        { filename: "posts/java/java.nio.charset.html", category: "Programming/Java/java.base", title: "java.nio.charset 12", top: true },
        { filename: "posts/java/java.nio.file.html", category: "Programming/Java/java.base", title: "java.nio.file" },
        { filename: "posts/java/java.text.html", category: "Programming/Java/java.base", title: "java.text" },
        { filename: "posts/java/java.time.html", category: "Programming/Java/java.base", title: "java.time" },
        { filename: "posts/java/java.time.format.html", category: "Programming/Java/java.base", title: "java.time.format" },
        { filename: "posts/java/java.util.html", category: "Programming/Java/java.base", title: "java.util Since 1.0" },
        { filename: "posts/java/java.util.concurrent.html", category: "Programming/Java/java.base", title: "java.util.concurrent" }, //, top: true
        { filename: "posts/java/java.util.concurrent.atomic.html", category: "Programming/Java/java.base", title: "java.util.concurrent.atomic" },
        { filename: "posts/java/java.util.function.html", category: "Programming/Java/java.base", title: "java.util.function" },
        { filename: "posts/java/java.util.regex.html", category: "Programming/Java/java.base", title: "java.util.regex" },
        { filename: "posts/java/java.util.stream.html", category: "Programming/Java/java.base", title: "java.util.stream" },
        { filename: "posts/java/java.util.zip.html", category: "Programming/Java/java.base", title: "java.util.zip" },
        { filename: "posts/java/com.sun.nio.sctp.html", category: "Programming/Java/jdk.sctp", title: "com.sun.nio.sctp 12 Since 1.7", top: true },

        { filename: "posts/javascript/basic.html", category: "Programming/JavaScript", title: "코어 JavaScript" },
        { filename: "posts/javascript/basic2.html", category: "Programming/JavaScript", title: "브라우저 JavaScript" },
        { filename: "posts/javascript/jquery.html", category: "Programming/JavaScript", title: "jQuery" },
        
        { filename: "posts/python/basic.html", category: "Programming/Python", title: "Python 시작하기", top: true },
        { filename: "posts/python/data_model.html", category: "Programming/Python", title: "Python 데이터 모델", top: true },
        { filename: "posts/python/built_in_function.html", category: "Programming/Python", title: "Built-in Functions", top: true },
        { filename: "posts/python/1904252130.html", category: "Programming/Python", title: "Built-in Constants" },//, top: true
        { filename: "posts/python/1904252131.html", category: "Programming/Python", title: "Built-in Types" },//, top: true
        { filename: "posts/python/1904252133.html", category: "Programming/Python", title: "Built-in Exceptions" },//, top: true
        { filename: "posts/python/1904252134.html", category: "Programming/Python", title: "Text Processing Services" },//, top: true
        { filename: "posts/python/1904252135.html", category: "Programming/Python", title: "Binary Data Services" },//, top: true
        { filename: "posts/python/1904252136.html", category: "Programming/Python", title: "Data Types" },//, top: true

        { filename: "posts/tip/docker.html", category: "Tip", title: "Docker" },
        { filename: "posts/tip/eclipse.html", category: "Tip", title: "Eclipse" },
        { filename: "posts/tip/giregi.html", category: "Tip", title: "기레기 고소 방법" },
        { filename: "posts/tip/hangul.html", category: "Tip", title: "한글 유니코드" },
        { filename: "posts/tip/tomcat.html", category: "Tip", title: "tomcat" },
        { filename: "posts/tip/linux.html", category: "Tip", title: "Linux" },
        { filename: "posts/tip/macro.html", category: "Tip", title: "macro" },
        { filename: "posts/tip/playstore.html", category: "Tip", title: "Google Play Store" },
        { filename: "posts/tip/psql.html", category: "Tip", title: "psql" },
        { filename: "posts/tip/regex.html", category: "Tip", title: "정규표현식" },
        { filename: "posts/tip/special_characters.html", category: "Tip", title: "특수문자 영문 이름" },
        { filename: "posts/tip/svg.html", category: "Tip", title: "SVG" },
        { filename: "posts/tip/unity.html", category: "Tip", title: "Unity", top: true },
        { filename: "posts/tip/vi.html", category: "Tip", title: "vi" },
        { filename: "posts/tip/vs_code.html", category: "Tip", title: "VS Code" },
        { filename: "posts/tip/windows.html", category: "Tip", title: "Windows" },
    ],
    tree: {},
    contents: [],
    visible: [],
    codes: {},
};

/*
lan 속성 : nohighlight, text, java, javascript, cpp, matlab, xml, css, ruby, sql, bash, shell, php, go, python, cs
<button class="btn-code" path="Repositories/Eclipse" lan="java">예시 코드 »</button>
<button type="button" class="btn btn-primary btn-sm" onclick="">실행 »</button>
<a role="button" target="_blank" class="btn btn-info btn-sm" href=""></a>
<a role="button" class="btn btn-info btn-sm" href="javascript:;"></a>
<img class="rounded mx-auto d-block" alt="" src="imgs/">
 */