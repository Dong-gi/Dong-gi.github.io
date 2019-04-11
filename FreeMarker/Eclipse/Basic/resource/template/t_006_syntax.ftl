<!doctype html>

<html lnag="ko">
    <head>
        <title>${title}</title>
    </head>

    <body>
        ${"\xc548\xb155\xd558\xc138\xc694"}<br>
        ${r"C:\Program Files\"}<br>

        <#list ["foo", "bar", 123.45, [title, 1+1]] as item>
            <#if item?is_enumerable>
                ${item?join(", ")}
            <#else>
                ${item}
            </#if>
            <#sep>, </#sep>
        </#list>
    </body>
</html>

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_005_type.ftl</title>
    </head>

    <body>
        hash value : 123.456, hello<br>
        hash iteration : (str, hello),(num, 123.456)<br>
        sequence value : 1, world<br>
        sequence iteration : 1,3.14,world // 3 items<br>
    </body>
</html>

-->

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_006_syntax.ftl</title>
    </head>

    <body>
        안녕하세요
    </body>
</html>


-->

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_006_syntax.ftl</title>
    </head>

    <body>
        안녕하세요<br>
        C:\Program Files\<br>
            foo
        ,             bar
        ,             123.45
        ,             t_006_syntax.ftl, 2

    </body>
</html>


-->

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_006_syntax.ftl</title>
    </head>

    <body>
        안녕하세요<br>
        C:\Program Files\<br>
        foo
        ,         bar
        ,         123.45
        ,         t_006_syntax.ftl, 2
        
    </body>
</html>


-->

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_006_syntax.ftl</title>
    </head>

    <body>
        안녕하세요<br>
        C:\Program Files\<br>
        foo,         bar,         123.45,         t_006_syntax.ftl, 2
        
    </body>
</html>


-->

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_006_syntax.ftl</title>
    </head>

    <body>
        안녕하세요<br>
        C:\Program Files\<br>
        foo,         bar,         123.45,         t_006_syntax.ftl, 2
    </body>
</html>


-->

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_006_syntax.ftl</title>
    </head>

    <body>
        안녕하세요<br>
        C:\Program Files\<br>

                foo
            , 
                bar
            , 
                123.45
            , 
                t_006_syntax.ftl, 2
            
    </body>
</html>


-->
