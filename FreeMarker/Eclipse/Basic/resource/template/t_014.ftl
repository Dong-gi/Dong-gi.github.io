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
<#list 1.. as _><#if x?node_type == "document"><#break></#if>${x?parent?node_name}<#assign x = x?parent><#sep> > </#sep></#list>


<#--
1. 이름으로 요소 접근
Donggi Kim

제목1

// 각 요소는 내부적으로 해시인 동시에 시퀀스이다.
Donggi Kim

    1장
        문단 1-1
        문단 1-2
    2장
        문단 2-1
        문단 2-2


2. 

-->

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


-->

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



-->

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
    type : text  text : FreeMarker template error:
The following has evaluated to null or missing:
==> c@text  [in template "t_014.ftl" at line 29, column 121]

----
Tip: If the failing expression is known to legally refer to something that's sometimes null or missing, either specify a default value like myOptionalVar!myDefault, or use <#if myOptionalVar??>when-present<#else>when-missing</#if>. (These only cover the last step of the expression; to cover the whole expression, use parenthesis: (myOptionalVar.foo)!myDefault, (myOptionalVar.foo)??
----

----
FTL stack trace ("~" means nesting-related):
	- Failed at: ${c@text}  [in template "t_014.ftl" at line 29, column 119]
----

Java stack trace (for programmers):
----
freemarker.core.InvalidReferenceException: [... Exception message was already printed; see it above ...]
	at freemarker.core.InvalidReferenceException.getInstance(InvalidReferenceException.java:134)
	at freemarker.core.EvalUtil.coerceModelToTextualCommon(EvalUtil.java:467)
	at freemarker.core.EvalUtil.coerceModelToStringOrMarkup(EvalUtil.java:389)
	at freemarker.core.EvalUtil.coerceModelToStringOrMarkup(EvalUtil.java:358)
	at freemarker.core.DollarVariable.calculateInterpolatedStringOrMarkup(DollarVariable.java:100)
	at freemarker.core.DollarVariable.accept(DollarVariable.java:63)
	at freemarker.core.Environment.visit(Environment.java:330)
	at freemarker.core.Environment.visit(Environment.java:372)
	at freemarker.core.IteratorBlock$IterationContext.executedNestedContentForCollOrSeqListing(IteratorBlock.java:317)
	at freemarker.core.IteratorBlock$IterationContext.executeNestedContent(IteratorBlock.java:271)
	at freemarker.core.IteratorBlock$IterationContext.accept(IteratorBlock.java:242)
	at freemarker.core.Environment.visitIteratorBlock(Environment.java:642)
	at freemarker.core.IteratorBlock.acceptWithResult(IteratorBlock.java:107)
	at freemarker.core.IteratorBlock.accept(IteratorBlock.java:93)
	at freemarker.core.Environment.visit(Environment.java:330)
	at freemarker.core.Environment.visit(Environment.java:336)
	at freemarker.core.Environment.process(Environment.java:309)
	at freemarker.template.Template.process(Template.java:384)
	at io.github.donggi.ProgrammerGuilde.process(ProgrammerGuilde.java:54)
	at io.github.donggi.ProgrammerGuilde.example_t_014(ProgrammerGuilde.java:31)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:628)
	at org.junit.jupiter.engine.execution.ExecutableInvoker.invoke(ExecutableInvoker.java:117)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$7(TestMethodTestDescriptor.java:184)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:180)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:127)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:68)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:135)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1540)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:38)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:139)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1540)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:38)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:139)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:32)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:57)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:51)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:229)
	at org.junit.platform.launcher.core.DefaultLauncher.lambda$execute$6(DefaultLauncher.java:197)
	at org.junit.platform.launcher.core.DefaultLauncher.withInterceptedStreams(DefaultLauncher.java:211)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:191)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:137)
	at org.eclipse.jdt.internal.junit5.runner.JUnit5TestReference.run(JUnit5TestReference.java:89)
	at org.eclipse.jdt.internal.junit.runner.TestExecution.run(TestExecution.java:41)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:541)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:763)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.run(RemoteTestRunner.java:463)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.main(RemoteTestRunner.java:209)

-->

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
    type : text  text : 
        
    type : element name : title 
    type : text  text : 
        
    type : element name : author 
    type : text  text : 
        
    type : element name : isbn 
    type : text  text : 
        
    type : element name : summary 
    type : text  text : 

        
    type : element name : text 
    type : text  text : 
    


-->

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
     type : text text : 
        
    name : title type : element 
     type : text text : 
        
    name : author type : element 
     type : text text : 
        
    name : isbn type : element 
     type : text text : 
        
    name : summary type : element 
     type : text text : 

        
    name : text type : element 
     type : text text : 
    


-->

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
    type : text 
        
    name : title type : element
    type : text 
        
    name : author type : element
    type : text 
        
    name : isbn type : element
    type : text 
        
    name : summary type : element
    type : text 

        
    name : text type : element
    type : text 
    


-->

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
    type : text text : 
        
    name : title type : element
    type : text text : 
        
    name : author type : element
    type : text text : 
        
    name : isbn type : element
    type : text text : 
        
    name : summary type : element
    type : text text : 

        
    name : text type : element
    type : text text : 
    


-->

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


-->

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

4. 속성 순회
    category = 금서
    reason = 그냥



-->

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



-->

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
    1
    2
    3
    4
    5
    6
    7
    8
    9
    10
    11
    12
    13
    14
    15
    16
    17
    18
    19
    20
    21
    22
    23
    24
    25
    26
    27
    28
    29
    30
    31
    32
    33
    34
    35
    36
    37
    38
    39
    40
    41
    42
    43
    44
    45
    46
    47
    48
    49
    50
    51
    52
    53
    54
    55
    56
    57
    58
    59
    60
    61
    62
    63
    64
    65
    66
    67
    68
    69
    70
    71
    72
    73
    74
    75
    76
    77
    78
    79
    80
    81
    82
    83
    84
    85
    86
    87
    88
    89
    90
    91
    92
    93
    94
    95
    96
    97
    98
    99
    100



-->

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
FreeMarker template error:
Can't compare values of these types. Allowed comparisons are between two numbers, two strings, two dates, or two booleans.
Left hand operand is an extended node+sequence+hash+string (com.sun.org.apache.xerces.internal.dom.DeferredElementNSImpl wrapped into f.e.dom.ElementModel).
Right hand operand is an extended node+sequence+hash (com.sun.org.apache.xerces.internal.dom.DeferredDocumentImpl wrapped into f.e.dom.DocumentModel).
The blamed expression:
==> x?parent == root  [in template "t_014.ftl" at line 46, column 22]

----
FTL stack trace ("~" means nesting-related):
	- Failed at: #if x?parent == root  [in template "t_014.ftl" at line 46, column 17]
----

Java stack trace (for programmers):
----
freemarker.core._MiscTemplateException: [... Exception message was already printed; see it above ...]
	at freemarker.core.EvalUtil.compare(EvalUtil.java:303)
	at freemarker.core.EvalUtil.compare(EvalUtil.java:115)
	at freemarker.core.ComparisonExpression.evalToBoolean(ComparisonExpression.java:62)
	at freemarker.core.ConditionalBlock.accept(ConditionalBlock.java:48)
	at freemarker.core.Environment.visit(Environment.java:366)
	at freemarker.core.IteratorBlock$IterationContext.executedNestedContentForCollOrSeqListing(IteratorBlock.java:291)
	at freemarker.core.IteratorBlock$IterationContext.executeNestedContent(IteratorBlock.java:271)
	at freemarker.core.IteratorBlock$IterationContext.accept(IteratorBlock.java:242)
	at freemarker.core.Environment.visitIteratorBlock(Environment.java:642)
	at freemarker.core.IteratorBlock.acceptWithResult(IteratorBlock.java:107)
	at freemarker.core.IteratorBlock.accept(IteratorBlock.java:93)
	at freemarker.core.Environment.visit(Environment.java:330)
	at freemarker.core.Environment.visit(Environment.java:336)
	at freemarker.core.Environment.process(Environment.java:309)
	at freemarker.template.Template.process(Template.java:384)
	at io.github.donggi.ProgrammerGuilde.process(ProgrammerGuilde.java:54)
	at io.github.donggi.ProgrammerGuilde.example_t_014(ProgrammerGuilde.java:31)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
	at java.base/jdk.internal.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)
	at java.base/jdk.internal.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
	at java.base/java.lang.reflect.Method.invoke(Method.java:566)
	at org.junit.platform.commons.util.ReflectionUtils.invokeMethod(ReflectionUtils.java:628)
	at org.junit.jupiter.engine.execution.ExecutableInvoker.invoke(ExecutableInvoker.java:117)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.lambda$invokeTestMethod$7(TestMethodTestDescriptor.java:184)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.invokeTestMethod(TestMethodTestDescriptor.java:180)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:127)
	at org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor.execute(TestMethodTestDescriptor.java:68)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:135)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1540)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:38)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:139)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
	at java.base/java.util.ArrayList.forEach(ArrayList.java:1540)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java:38)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$5(NodeTestTask.java:139)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$7(NodeTestTask.java:125)
	at org.junit.platform.engine.support.hierarchical.Node.around(Node.java:135)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java:123)
	at org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java:73)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java:122)
	at org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java:80)
	at org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.submit(SameThreadHierarchicalTestExecutorService.java:32)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor.execute(HierarchicalTestExecutor.java:57)
	at org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine.execute(HierarchicalTestEngine.java:51)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:229)
	at org.junit.platform.launcher.core.DefaultLauncher.lambda$execute$6(DefaultLauncher.java:197)
	at org.junit.platform.launcher.core.DefaultLauncher.withInterceptedStreams(DefaultLauncher.java:211)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:191)
	at org.junit.platform.launcher.core.DefaultLauncher.execute(DefaultLauncher.java:137)
	at org.eclipse.jdt.internal.junit5.runner.JUnit5TestReference.run(JUnit5TestReference.java:89)
	at org.eclipse.jdt.internal.junit.runner.TestExecution.run(TestExecution.java:41)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:541)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:763)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.run(RemoteTestRunner.java:463)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.main(RemoteTestRunner.java:209)

-->

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
document, @document



-->

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
chapter > text > book > bookshelf > @document > 



-->
