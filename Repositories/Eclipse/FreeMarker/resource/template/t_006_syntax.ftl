<!doctype html>

<html lnag="ko">
    <head>
        <title>${title}</title>
    </head>

    <body>
        String literal 1 : ${"\xc548\xb155\xd558\xc138\xc694"}<br>
        String leteral 2 : ${r"C:\Program Files\"}<br>
        <br>
        Sequence literal :
        <#list ["foo", "bar", 123.45, [title, 1+1]] as item>
            <#if item?is_enumerable>
                ${item?join(", ")}
            <#else>
                ${item}
            </#if>
            <#sep>, </#sep>
        </#list><br>
        <br>
        Range literal :
        <ul>
            <li>${(1..4)?join(", ")}</li>
            <li>${(4..1)?join(", ")}</li>
            <li>${(1..!4)?join(", ")}</li>
            <li>${(4..!1)?join(", ")}</li>
            <li>${(1..*4)?join(", ")}</li>
            <li>${(1..*-4)?join(", ")}</li>
            <li><#list 1.. as idx><#if idx gt 10><#break></#if>${idx},</#list></li>
        </ul>
    </body>
</html>

<#--
<!doctype html>

<html lnag="ko">
    <head>
        <title>t_006_syntax.ftl</title>
    </head>

    <body>
        String literal 1 : 안녕하세요<br>
        String leteral 2 : C:\Program Files\<br>
        <br>
        Sequence literal :
                foo
            ,
                        bar
            ,
                        123.45
            ,
                        t_006_syntax.ftl, 2

        <br>
        <br>
        Range literal :
        <ul>
            <li>1, 2, 3, 4</li>
            <li>4, 3, 2, 1</li>
            <li>1, 2, 3</li>
            <li>4, 3, 2</li>
            <li>1, 2, 3, 4</li>
            <li>1, 0, -1, -2</li>
            <li>1,2,3,4,5,6,7,8,9,10,</li>
        </ul>
    </body>
</html>

-->
