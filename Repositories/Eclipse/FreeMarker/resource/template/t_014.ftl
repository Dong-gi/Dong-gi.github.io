1. 이름으로 요소 접근
${root.bookshelf.owner.name}

// 각 요소는 내부적으로 해시인 동시에 시퀀스이다.
${root.bookshelf[0].owner[0].name[0]}

${root.bookshelf.book[0].title}

<#list root.bookshelf.book[0].text.chapter as chapter>
    ${chapter.title}
    <#list chapter.p as p>
        ${p}
    </#list>
</#list>


2. 요소의 속성 접근
<#list root.bookshelf.book as book>
    "${book.title}"의 카테고리 = ${book.@category}
</#list>

<#list root.bookshelf.book.@category as category>
    ${category}
</#list>


3. 자식 요소 순회
<#list root.bookshelf.book[1]?children as c>
    <#if c?node_type == 'element'>name : ${c?node_name}, </#if>type : ${c?node_type}<#if c?node_type == 'text'>, text : "${c}"</#if>
</#list>

↑ 여기에는 노드 사이의 공백이 text로 포함된다. element 자식 요소만 순회하려면 아래를 이용.
<#list root.bookshelf.book[1].* as c>
    <#if c?node_type == 'element'>name : ${c?node_name}, </#if>type : ${c?node_type}<#if c?node_type == 'text'>, text : "${c}"</#if>
</#list>


4. 속성 순회
<#list root.bookshelf.book[1].@@ as attr>
    ${attr?node_name} = ${attr}
</#list>


5. 부모 노드 접근
<#assign x = root.bookshelf.book[0].text.chapter[1].p[1]>
<#list 1.. as _><#if x?node_type == "document"><#break></#if>${x?parent?node_name}<#assign x = x?parent> → </#list>

<#--
1. 이름으로 요소 접근
Donggi Kim

// 각 요소는 내부적으로 해시인 동시에 시퀀스이다.
Donggi Kim

제목1

    1장
        문단 1-1
        문단 1-2
    2장
        문단 2-1
        문단 2-2


2. 요소의 속성 접근
    "제목1"의 카테고리 = 문학
    "제목2"의 카테고리 = 금서

    문학
    금서


3. 자식 요소 순회
    type : text, text : "
        "
    name : title, type : element
    type : text, text : "
        "
    name : author, type : element
    type : text, text : "
        "
    name : isbn, type : element
    type : text, text : "
        "
    name : summary, type : element
    type : text, text : "

        "
    name : text, type : element
    type : text, text : "
    "

↑ 여기에는 노드 사이의 공백이 text로 포함된다. element 자식 요소만 순회하려면 아래를 이용.
    name : title, type : element
    name : author, type : element
    name : isbn, type : element
    name : summary, type : element
    name : text, type : element


4. 속성 순회
    category = 금서
    reason = 그냥


5. 부모 노드 접근
chapter → text → book → bookshelf → @document →

-->
