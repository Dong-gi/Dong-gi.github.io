<!doctype html>

<html lnag="ko">
    <head>
        <title>${title}</title>
    </head>

    <body>
        hash value : ${hash.num}, ${hash.str}<br>
        hash iteration : <#list hash as key, value>(${key}, ${value})<#sep>,</#sep></#list><br>
        sequence value : ${sequence[0]}, ${sequence[2]}<br>
        sequence iteration : <#list sequence as item>${item}<#sep>,</#sep></#list> // ${sequence?size} items<br>
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
