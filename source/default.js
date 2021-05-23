const posts = {list: [
    { category: 'Algorithm',           file: '/posts/algorithm/algo.html',                      title: '1 알고리즘 이론' },
    { category: 'Algorithm',           file: '/posts/algorithm/mcs.html',                       title: '2 컴퓨터공학도를 위한 수학' },
    { category: 'Algorithm',           file: '/posts/algorithm/linear_algebra.html',            title: '3 선형대수' },
    { category: 'Algorithm',           file: '/posts/algorithm/probability.html',               title: '4 확률' },
    { category: 'Algorithm/KOREATECH', file: '/posts/algorithm/koreatech/1003.html',            title: '1003: 0을 만들자' },
    { category: 'Algorithm/KOREATECH', file: '/posts/algorithm/koreatech/1008.html',            title: '1008: 순환 소수' },
    { category: 'Algorithm/KOREATECH', file: '/posts/algorithm/koreatech/1010.html',            title: '소수(Prime) 관련 문제' },
    { category: 'Algorithm/KOREATECH', file: '/posts/algorithm/koreatech/1011.html',            title: '동적계획법(DP) 문제' },
    { category: 'Algorithm/KOREATECH', file: '/posts/algorithm/koreatech/1018.html',            title: '1018: 문자열 거리 최소화 하기' },
    { category: 'Algorithm/KOREATECH', file: '/posts/algorithm/koreatech/1034.html',            title: '1034, 1041: 최소 이동 거리' },
    { category: 'Algorithm/KOREATECH', file: '/posts/algorithm/koreatech/1048.html',            title: '1048: AP 배분' },
    { category: 'Algorithm/KOREATECH', file: '/posts/algorithm/koreatech/1095.html',            title: '1095: 자연스러운 정렬' },

    { category: 'JVM',                 file: '/posts/java/basic.html',                          title: '1-1 Java 시작하기' },
    { category: 'JVM',                 file: '/posts/java/version.html',                        title: '1-2 Java 버전별 추가사항' },
    { category: 'JVM',                 file: '/posts/java/performance.html',                    title: '1-3 JVM 옵션 및 성능 관련사항' },
    { category: 'JVM',                 file: '/posts/java/jni.html',                            title: '1-4 Java Native Interface' },
    { category: 'JVM',                 file: '/posts/java/effective_java.html',                 title: '1-5 『Effective Java』' },
    { category: 'JVM',                 file: '/posts/java/java_ee.html',                        title: '2-1 JavaEE' },
    { category: 'JVM',                 file: '/posts/java/jpa.html',                            title: '2-2 JPA; Java Persistence API' },
    { category: 'JVM',                 file: '/posts/java/spring_framework.html',               title: '2-3 Spring Framework' },
    { category: 'JVM',                 file: '/posts/java/spring_servlet.html',                 title: '2-3-5 Web Servlet' },
    { category: 'JVM',                 file: '/posts/java/spring_reactive.html',                title: '2-3-6 Web Reactive' },
    { category: 'JVM',                 file: '/posts/java/javafx.html',                         title: '3 JavaFX' },
    { category: 'JVM',                 file: '/posts/java/android.html',                        title: '4 Android' },
    { category: 'JVM',                 file: '/posts/single/groovy.html',                       title: '5 Groovy' },
    { category: 'JVM',                 file: '/posts/project/reminder.html',                    title: 'x 할일 목록 - Spring Boot 토이 프로젝트' },
    { category: 'JVM/Library',         file: '/posts/java/java.base.html',                      title: '1-1 java.base 12' },
    { category: 'JVM/Library',         file: '/posts/java/javax.annotation.processing.html',    title: '1-2 Annotation Processing API' },
    { category: 'JVM/Library',         file: '/posts/java/com.sun.nio.sctp.html',               title: '1-3 com.sun.nio.sctp Since 1.7' },
    { category: 'JVM/Library',         file: '/posts/java/lombok.html',                         title: '2 lombok 1.18.10' },
    { category: 'JVM/Library',         file: '/posts/java/apache.commons.collections.html',     title: '3-1 Apache Commons Collections 4.4' },
    { category: 'JVM/Library',         file: '/posts/java/apache.commons.lang.html',            title: '3-2 Apache Commons Lang 3.9' },
    { category: 'JVM/Library',         file: '/posts/java/apache.commons.rng.html',             title: '3-3 Apache Commons RNG Core 1.2' },
    { category: 'JVM/Library',         file: '/posts/java/apache.commons.math.html',            title: '3-4 Apache Commons Math 3.6.1' },
    { category: 'JVM/Library',         file: '/posts/front/freemarker/template_guide.html',     title: '4-1 FreeMarker 템플릿 작성 가이드 2.3.28' },
    { category: 'JVM/Library',         file: '/posts/front/freemarker/programming_guide.html',  title: '4-2 FreeMarker 프로그래밍 가이드 2.3.28' },
    { category: 'JVM/Library',         file: '/posts/front/freemarker/built_in.html',           title: '4-3 FreeMarker Built-in 목록 2.3.28' },
    { category: 'JVM/Library',         file: '/posts/front/freemarker/xml_guide.html',          title: '4-4 FreeMarker XML 처리 가이드 2.3.28' },
    { category: 'JVM/Library',         file: '/posts/java/jackson.html',                        title: '5-1 Jackson 2.10.1' },
    { category: 'JVM/Library',         file: '/posts/java/gson.html',                           title: '5-2 Gson 2.8.6' },
    { category: 'JVM/Library',         file: '/posts/java/logback.html',                        title: '6 Logback with SLF4J' },
    { category: 'JVM/Library',         file: '/posts/java/guava.html',                          title: '7 Guava 30.1' },
    { category: 'Infra',               file: '/posts/single/aws.html',                          title: 'AWS' },
    { category: 'Infra',               file: '/posts/infra/linux.html',                         title: 'Linux' },
    { category: 'Infra',               file: '/posts/single/docker.html',                       title: 'Docker' },
    { category: 'Infra',               file: '/posts/single/git.html',                          title: 'Git' },
    { category: 'Infra',               file: '/posts/single/gradle.html',                       title: 'Gradle' },
    { category: 'Infra',               file: '/posts/single/nginx.html',                        title: 'Nginx' },
    { category: 'Infra',               file: '/posts/single/rpi.html',                          title: 'Raspberry Pi 3 + OpenCV(python)' },
    { category: 'Infra',               file: '/posts/single/sdkman.html',                       title: 'SDKMAN; The Software Development Kit Manager' },
    { category: 'Infra',               file: '/posts/single/tomcat.html',                       title: 'tomcat' },
    { category: 'Infra',               file: '/posts/infra/cicd.html',                          title: 'CI/CD' },
    { category: 'Infra',               file: '/posts/infra/oauth.html',                         title: 'OAuth' },
    { category: 'Infra/DB',            file: '/posts/db/concept.html',                          title: '1 DB 기초' },
    { category: 'Infra/DB',            file: '/posts/db/psql_tutorial.html',                    title: '2-1 PostgreSQL 시작하기' },
    { category: 'Infra/DB',            file: '/posts/db/psql_sql.html',                         title: '2-2 PostgreSQL SQL 언어' },
    { category: 'Infra/DB',            file: '/posts/db/psql_admin.html',                       title: '2-3 PostgreSQL 서버 관리' },
    { category: 'Infra/DB',            file: '/posts/db/psql_programming.html',                 title: '2-4 PostgreSQL 서버 프로그래밍' },
    { category: 'Infra/DB',            file: '/posts/db/psql_to_sqlite.html',                   title: '2-5 PostgreSQL → SQLite3 마이그레이션' },
    { category: 'Infra/DB',            file: '/posts/db/mysql.html',                            title: '3 MySQL' },
    { category: 'Infra/DB',            file: '/posts/db/mongodb.html',                          title: '4 MongoDB' },
    { category: 'Infra/DB',            file: '/posts/db/redis.html',                            title: '5 Redis' },
    { category: 'Topic',               file: '/posts/single/fp.html',                           title: 'FP; Functional Programming' },
    { category: 'Topic',               file: '/posts/single/interview.html',                    title: '개발자 면접 준비' },
    { category: 'Topic',               file: '/posts/single/refactoring.html',                  title: '디자인 패턴 + 리팩터링' },
    { category: 'Topic',               file: '/posts/single/quantum_computer.html',             title: '양자 컴퓨터' },
    { category: 'Topic/Book',          file: '/posts/book/001.html',                            title: '1만 시간의 재발견' },
    { category: 'Topic/Book',          file: '/posts/book/002.html',                            title: '마음의 탄생' },
    { category: 'Topic/Book',          file: '/posts/book/003.html',                            title: '생각하는 삶을 위한 철학의 역사' },
    { category: 'Topic/Book',          file: '/posts/book/004.html',                            title: '쇼펜하우어, 돌이 별이 되는 철학' },
    { category: 'Topic/Book',          file: '/posts/book/005.html',                            title: '인생의 모든 의미' },
    { category: 'Topic/Book',          file: '/posts/book/006.html',                            title: '지식의 착각' },
    { category: 'Topic/Book',          file: '/posts/book/007.html',                            title: '키르케고르 실존 극장' },
    { category: 'Topic/Book',          file: '/posts/book/008.html',                            title: 'Gamification by Design' },
    { category: 'Topic/Book',          file: '/posts/book/009.html',                            title: '인지 편향' },
    { category: '.NET',                file: '/posts/dotnet/csharp_basic.html',                 title: '1-1 C# 기초' },
    { category: '.NET',                file: '/posts/dotnet/csharp_library.html',               title: '1-2 C# 라이브러리' },
    { category: '.NET',                file: '/posts/dotnet/wpf_basic.html',                    title: '2 WPF 기초' },
    { category: '.NET',                file: '/posts/single/unity.html',                        title: '3 Unity' },
    { category: '.NET',                file: '/posts/project/wpf_data_tool.html',               title: 'x 데이터 작업 툴 - C#.NET, WPF' },
    { category: 'JavaScript',          file: '/posts/javascript/basic.html',                    title: '1-1 코어 JavaScript' },
    { category: 'JavaScript',          file: '/posts/javascript/basic2.html',                   title: '1-2 브라우저 JavaScript' },
    { category: 'JavaScript',          file: '/posts/javascript/jquery.html',                   title: '1-3 jQuery' },
    { category: 'JavaScript',          file: '/posts/javascript/node.html',                     title: '2 Node' },
    { category: 'JavaScript',          file: '/posts/project/sf.html',                          title: 'x SF; Simplest html Framework' },
    { category: 'Web',                 file: '/posts/front/http.html',                          title: '1 HTTP 1.1' },
    { category: 'Web',                 file: '/posts/front/html.html',                          title: '2 HTML' },
    { category: 'Web',                 file: '/posts/front/css.html',                           title: '3 CSS' },
    { category: 'Web',                 file: '/posts/single/nginx.html',                        title: 'x Nginx' },
    { category: 'Web',                 file: '/posts/single/tomcat.html',                       title: 'x tomcat' },
    { category: 'Web',                 file: '/posts/javascript/basic2.html',                   title: 'x 브라우저 JavaScript' },
    { category: 'Web',                 file: '/posts/javascript/jquery.html',                   title: 'x jQuery' },
    { category: 'Web',                 file: '/posts/java/java_ee.html',                        title: 'x JavaEE' },
    { category: 'Web',                 file: '/posts/javascript/node.html',                     title: 'x Node' },
    { category: 'Web',                 file: '/posts/front/freemarker/template_guide.html',     title: 'x FreeMarker 템플릿 작성 가이드 2.3.28' },
    { category: 'Web',                 file: '/posts/front/freemarker/programming_guide.html',  title: 'x FreeMarker 프로그래밍 가이드 2.3.28' },
    { category: 'Web',                 file: '/posts/front/freemarker/built_in.html',           title: 'x FreeMarker Built-in 목록 2.3.28' },
    { category: 'Web',                 file: '/posts/front/freemarker/xml_guide.html',          title: 'x FreeMarker XML 처리 가이드 2.3.28' },
    { category: 'Web',                 file: '/posts/project/sf.html',                          title: 'x SF; Simplest html Framework' },
    { category: 'Web',                 file: '/posts/project/reminder.html',                    title: 'x 할일 목록 - Spring Boot 토이 프로젝트' },
    { category: 'Python',              file: '/posts/python/basic.html',                        title: '1-1 Python 3.8 시작하기' },
    { category: 'Python',              file: '/posts/python/built_in_function.html',            title: '1-2 Python Built-in Functions 3.8' },
    { category: 'Python',              file: '/posts/python/built_in_constant.html',            title: '1-3 Python Built-in Constants 3.8' },
    { category: 'Python',              file: '/posts/python/built_in_type.html',                title: '1-4 Python Built-in Types 3.8' },
    { category: 'Python',              file: '/posts/python/built_in_exception.html',           title: '1-5 Python Built-in Exceptions 3.8' },
    { category: 'Python',              file: '/posts/python/data_model.html',                   title: '1-6 Python 3.8 데이터 모델' },
    { category: 'Python',              file: '/posts/python/standard.html',                     title: '1-7 표준 라이브러리' },
    { category: 'Python',              file: '/posts/python/persistence.html',                  title: '1-8 Python Data Persistence' },
    { category: 'Python',              file: '/posts/python/pandas.html',                       title: '3rd/pandas : 데이터 처리' },
    { category: 'Project',             file: '/posts/project/error.html',                       title: '1-1 경험한 예외' },
    { category: 'Project',             file: '/posts/project/tip.html',                         title: '1-2 잡다한 팁' },
    { category: 'Project',             file: '/posts/project/snippets.html',                    title: '1-3 코드 조각' },
    { category: 'Project',             file: '/posts/project/wpf_data_tool.html',               title: '데이터 작업 툴 - C#.NET, WPF' },
    { category: 'Project',             file: '/posts/project/sf.html',                          title: 'SF; Simplest html Framework' },
    { category: 'Project',             file: '/posts/project/reminder.html',                    title: '할일 목록 - Spring Boot 토이 프로젝트' },
    { category: 'Project',             file: '/posts/single/demo3.html',                        title: '로마숫자 사칙연산 계산기' },

    { category: 'Zzz 🚮', file: '/posts/algorithm/ai.html', title: 'AI' },
    { category: 'Zzz 🚮', file: '/posts/ruby/basic.html', title: 'Ruby Basic' },
], codes: {}}

window.addEventListener('load', () => {
    new MutationObserver(mutationCallback).observe(document.body, { attributes: false, childList: true, subtree: true })
    mutationCallback([{ type: 'childList', target: document.body }])

    /* 하이라이팅 지원 목록 */
    console.log(hljs.listLanguages())

    updateSidebar()
    updatePostList()
    insertDisqusThread()
    updateMarkerList()
    window.onscroll = SFUtil.debounce(function () {
        if (document.getElementById('sidebar').style.display == 'none') return;
        for (let marker of document.querySelectorAll('.marker')) {
            if (SFUtil.isElementInViewport(marker)) {
                document.querySelector(`li[marker-id=${marker.getAttribute('marker-id')}]`).scrollIntoView()
                return;
            }
        }
    }, 300)
    document.getElementById('query').onkeyup = SFUtil.debounce(queryUpdated, 500)
    SFUtil.addOrderedTableFunctionality()

    for (let goto of document.querySelectorAll('.goto')) {
        goto.addEventListener('mousedown', function (e) {
            localStorage.setItem(`${location.href}-lastPos`, window.scrollY)
        })
    }
    window.onpopstate = function (e) {
        if (localStorage.getItem(`${location.href}-lastPos`)) {
            window.scrollTo({ top: localStorage.getItem(`${location.href}-lastPos`) })
            localStorage.removeItem(`${location.href}-lastPos`)
            return;
        }
        let goto = /#(pos-?\d+)/.exec(location.hash)
        if (goto) {
            let target = document.getElementById(goto[1])
            let parent = target
            while (parent.tagName != 'BODY') {
                if (parent.tagName == 'DETAILS')
                    parent.open = true
                parent = parent.parentElement
            }
            let arg = {
                top: SFUtil.getOffsetTop(target) - document.getElementById('nav').clientHeight
            }
            setTimeout(() => window.scrollTo(arg), 100)
            while (!target.clientHeight)
                target = target.parentElement
            SFUtil.highlight(target)
        }
    }
})

function mutationCallback(mutations, observer) {
    for (let mutation of mutations) {
        if (mutation.type !== 'childList') return
        addImageOnclick(mutation.target.querySelectorAll('img'))
        addCodeBtnOnclick(mutation.target.querySelectorAll('button.btn-code'))
        addHoverContents(mutation.target.querySelectorAll('.hover-content'))
        convertAsCodeDiv(mutation.target.querySelectorAll('.as-code'))
    }
}

function addImageOnclick(imgs) {
    for (let img of imgs) {
        if (!!img.onclick)
            continue
        img.onclick = ((src) => function (e) { SFUtil.openLink(src, '_blank'); })(img.src)
    }
}

function addCodeBtnOnclick(btns) {
    for (let button of btns) {
        let id = `${new Date().getTime()}-${Math.random().toString().replace(/\./g, '')}`
        button.id = `code-button-${id}`
        button.classList.remove('btn-code')
        button.onclick = insertCodeDiv(id)
    }
}

function addHoverContents(targets) {
    for (let hoverContent of targets)
        SFUtil.addHoverContent(hoverContent, document.getElementById(hoverContent.getAttribute('template-id')))
}

function convertAsCodeDiv(divs) {
    for (let div of divs) {
        let code = div.innerHTML.trim().replace(/&lt;/gm, '<').replace(/&gt;/gm, '>').replace(/&amp;/gm, '&')
        // console.log(code)
        div.innerHTML = ''
        fillCodeDiv(div, div.getAttribute('lan'), code)
        div.style.maxHeight = window.innerHeight / 2 + 'px'
        div.classList.remove('as-code')
    }
}

function isNarrow() {
    return document.getElementById('nav').clientWidth <= 600
}

function updateSidebar() {
    if (isNarrow())
        closeSidebar()
    else
        openSidebar()
    document.getElementById('sidebar').style.width = '333px'

    let fileMap = { files: [] }
    for (let file of fileText.trim().replace(/\r/gm, '').split('\n').sort(SFUtil.compareString)) {
        let subMap = fileMap
        let dirs = file.split('/')
        let filename = dirs.pop()
        for (let dir of dirs) {
            if (!subMap.hasOwnProperty(dir)) {
                subMap[dir] = {}
                subMap[dir].files = []
            }
            subMap = subMap[dir]
        }
        subMap.files.push(filename)
    }
    let url = URL.createObjectURL(new Blob([SFUtil.makeLSlikeText('git', fileMap, 'files')], {
        type: 'text/plain;charset=utf-8;'
    }))
    new DKFileList(url, '#file-list', null, false, null, true, () => {
        document.querySelector('#file-list>details').open = false
    })
}

function openSidebar() {
    document.getElementById('main').style.marginLeft = '333px'
    document.getElementById('sidebar').style.display = 'block'
}

function closeSidebar() {
    document.getElementById('main').style.marginLeft = '0'
    document.getElementById('sidebar').style.display = 'none'
}

function toggleSidebar() {
    if (document.getElementById('sidebar').style.display == 'none')
        openSidebar()
    else
        closeSidebar()
}

function updatePostList() {
    posts.list.sort((post1, post2) => SFUtil.compareString(post1.title, post2.title))
    posts.list.sort((post1, post2) => SFUtil.compareString(post1.category, post2.category))

    let categoryMap = { posts: [] }
    for (let post of posts.list) {
        let category = categoryMap
        for (let cate of post.category.split('/')) {
            if (!category.hasOwnProperty(cate)) {
                category[cate] = {}
                category[cate].posts = []
            }
            category = category[cate]
        }
        category.posts.push(post.title)
    }
    let url = URL.createObjectURL(new Blob([SFUtil.makeLSlikeText('카테고리', categoryMap, 'posts')], {
        type: 'text/plain;charset=utf-8;'
    }))
    new DKFileList(url, '#post-list', null, true, (category, title) => {
        let post = posts.list.filter(x => x.title == title)[0]
        return `<li><a href="${post.file}">${title}</a></li>`.asSF().$
    }, true, () => {
        document.querySelectorAll('#post-list details').forEach(x => x.open = false)
        let limit = document.getElementById('post-list')
        let anchors = document.querySelectorAll(`#post-list a[href="${location.pathname}"]`)
        if (anchors.length) {
            for (let a of anchors) {
                let p = a.parentElement
                p.classList.add('w3-yellow')
                while (limit != p) {
                    if (p.tagName == 'DETAILS')
                        p.open = true
                    p = p.parentElement
                }
            }
        } else {
            document.querySelector('#post-list details').open = true
        }
    })
}

function insertDisqusThread() {
    let parent = document.querySelector('div#contents')
    if (!!document.querySelector('div#disqus_thread')) {
        DISQUS.reset({
            reload: true,
            config: function () {
                this.page.url = `https://dong-gi.github.io${location.pathname}`
                this.page.identifier = location.pathname
            }
        })
        parent.append(document.querySelector('div#disqus_thread'))
        return
    }

    parent.append('<div id="disqus_thread" class="w3-padding-16"></div>'.asSF().$)
    eval(`var disqus_config = function () {
            this.page.url = 'https://dong-gi.github.io${location.pathname}';
            this.page.identifier = '${location.pathname}';
        };
        (function() {
            var d = document, s = d.createElement('script');
            s.src = 'https://donggi.disqus.com/embed.js';
            s.setAttribute('data-timestamp', +new Date());
            document.querySelector('div#disqus_thread').append(s);
        })();`)
}

function updateMarkerList() {
    for (let h of document.querySelectorAll('h1,h2,h3,h4,h5,h6'))
        h.classList.add('marker')

    let markerMap = { markers: [] }
    let id = new Date().getTime()
    for (let marker of document.querySelectorAll('.marker')) {
        marker.setAttribute('marker-id', `marker-${id++}`)
        markerMap.markers.push(marker.getAttribute('marker-id'))
    }
    let url = URL.createObjectURL(new Blob([SFUtil.makeLSlikeText('컨텐츠', markerMap, 'markers')], {
        type: 'text/plain;charset=utf-8;'
    }))
    new DKFileList(url, '#marker-list', (_, markerId) => {
        if (isNarrow())
            closeSidebar()
        let target = document.querySelector(`.marker[marker-id=${markerId}]`)
        let parent = target
        while (parent.tagName != 'BODY') {
            if (parent.tagName == 'DETAILS')
                parent.open = true
            parent = parent.parentElement
        }
        SFUtil.highlight(target)
        setTimeout(((target) => function () {
            window.scrollTo({
                top: SFUtil.getOffsetTop(target) - document.getElementById('nav').clientHeight
            })
        })(target), isNarrow() ? 444 : 0)
    }, true, (_, markerId) => {
        let target = document.querySelector(`.marker[marker-id=${markerId}]`)
        let name = getMarkerName(target)
        let li = `<li class="${target.classList.contains('fake') ? 'w3-hide' : ''}" title="${name}" marker-id="${markerId}">${name.substr(0, 25)}</li>`.asSF().$

        let main = document.querySelector('div#contents')
        let level = 0
        const skipTagNames = new Set(['THEAD', 'TBODY', 'TR', 'SPAN'])
        while (target.parentElement != main) {
            target = target.parentElement
            if (skipTagNames.has(target.tagName))
                continue
            level += 1
        }
        li.classList.add(`margin-left-${level}`)
        li.setAttribute('level', level)
        return li
    }, false, () => {
        let levels = [0, 0, 0, 0, 0, 0, 0, 0]
        const headerTags = new Set(['H1', 'H2', 'H3', 'H4', 'H5', 'H6'])
        for (let li of document.getElementById('marker-list').querySelectorAll('li')) {
            let current = parseInt(li.getAttribute('level'))
            levels[current]++
            levels.fill(0, current + 1)
            let prefix = `${levels.filter(x => x > 0).join('.')}. `
            li.innerText = `${prefix}${li.innerText}`
            let target = document.querySelector(`.marker[marker-id=${li.getAttribute('marker-id')}]`)
            if (headerTags.has(target.tagName))
                target.innerHTML = `${prefix}${target.innerHTML}`
        }
    })
}

function getMarkerName(marker) {
    let name
    switch (marker.tagName) {
        case 'IMG':
            name = `이미지 : ${marker.alt}`
            break
        case 'TABLE':
            name = `표 : ${marker.caption.textContent}`
            break
        default:
            name = marker.textContent
    }
    return name.replace(/\//gm, '\\')
}

function queryUpdated(e) {
    if (e.keyCode == 13) {
        SFUtil.openLink(`https://github.com/Dong-gi/Dong-gi.github.io/search?q=${e.target.value}`, '_blank')
        e.stopPropagation()
        return false
    }
    return true
}

function insertCodeDiv(id) {
    return () => {
        if (!document.getElementById(`code-div-${id}`)) {
            let button = document.getElementById(`code-button-${id}`)
            let path = button.title
            let xhr = new XMLHttpRequest()
            xhr.addEventListener('load', ((button) => function (e) {
                let div = `<div id="code-div-${id}" class="w3-leftbar w3-border-green code-div" style="max-height:${window.innerHeight / 2}"></div>`.asSF().$
                let lan = button.getAttribute('lan')
                if (this.status != 200)
                    this.responseText = 'Ajax Failed'

                posts.codes[id] = this.responseText
                fillCodeDiv(div, lan, this.responseText, button.getAttribute('displayRange'))
                div.style.maxHeight = window.innerHeight / 2 + 'px'

                if (lan != 'nohighlight') {
                    let modal = '<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-blue">모달로 보기</button>'.asSF().$
                    modal.onclick = showModal(id)
                    button.after(modal)
                    modal.after(div)
                } else
                    button.after(div)

                if (lan == 'javascript') {
                    let script = '<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-green">실행</button>'.asSF().$
                    script.onclick = () => eval(posts.codes[id])
                    button.after(script)
                }
            })(button))
            if (!/dong-gi\.github\.io/i.test(location.host))
                xhr.open('GET', `${path.startsWith('/') ? '' : './'}${path.replace(/ /gm, '%20')}`, true)
            else
                xhr.open('GET', `${path.startsWith('/') ? '' : './'}${path.replace(/ /gm, '%20')}?${new Date().getTime()}`, true)
            xhr.send()
        } else {
            let div = document.getElementById(`code-div-${id}`)
            SFUtil.toggleClass(div, ['w3-hide'])
            div.style.maxHeight = window.innerHeight / 2 + 'px'
        }
    }
}

function fillCodeDiv(div, lan, text, displayRange) {
    if (lan != 'nohighlight') {
        let code = text.replace(/\t/gm, '    ')
        code = code.replace(/ /gm, '  ')

        let lines = null
        if (lan === 'text')
            lines = code.split(/\n/gm)
        else
            lines = hljs.highlight(lan, code)['value'].split(/\n/gm)

        let ol = document.createElement('ol')
        displayRange = JSON.parse(displayRange || '[1, 100000000]') || [1, 100000000]
        if (displayRange.length % 2 == 1)
            displayRange.push(100000000)
        displayRange = displayRange.reverse()

        while (displayRange.length > 0) {
            let displayStart = displayRange.pop() - 1
            let displayEnd = displayRange.pop()
            for (let idx = displayStart; idx < displayEnd && idx < lines.length; ++idx) {
                if (lan === 'text') {
                    let li = document.createElement('li')
                    li.innerText = lines[idx].replace(/  /gm, '\u00A0')
                    ol.append(li)
                } else
                    ol.append(`<li>${lines[idx].replace(/  /gm, '&nbsp;')}</li>`.asSF().$)
            }
            if (displayRange.length > 0)
                ol.append(document.createElement('hr'))
        }
        div.append(ol)
    } else {
        let sfs = text.asSF()
        if (Array.isArray(sfs)) {
            for (let sf of sfs)
                div.append(sf.$)
        } else {
            div.append(sfs.$)
        }
    }
}

function showModal(id) {
    return () => {
        closeSidebar()
        let modal = document.getElementById(`modal-${id}`)
        if (!!modal) {
            modal.style.display = 'block'
            return
        }

        modal = getCodeModalHTML(id, document.getElementById(`code-button-${id}`).title.split('/').pop()).asSF().$
        let header = modal.querySelector('header')
        let body = modal.querySelector('.code-modal-body')
        let footer = modal.querySelector('footer')

        document.body.append(modal)
        modal.style.display = 'block'
        body.innerHTML = document.getElementById(`code-div-${id}`).innerHTML
        body.style.height = window.innerHeight - parseFloat(window.getComputedStyle(header).height)

        footer.querySelector('button.copy').onclick = () => {
            SFUtil.copyTextToCilpboard(posts.codes[id], modal)
            SFUtil.showSnackbar('복사 완료', modal)
            modal.focus()
        }
        footer.querySelector('button.download').onclick = () => SFUtil.downloadText(posts.codes[id], document.getElementById(`code-button-${id}`).title.split('/').pop())
        footer.querySelector('button.print').onclick = () => SFUtil.printElement(body)
        for (let node of modal.querySelectorAll('.w3-btn.close')) {
            node.onclick = () => {
                document.getElementById(`modal-${id}`).style.display = 'none'
                if (!isNarrow())
                    openSidebar()
            }
        }
    }
}

function getCodeModalHTML(id, filename) {
    return `<div id="modal-${id}" class="w3-modal code-modal">
    <div class="w3-modal-content">
        <header class="w3-container">
            <h2 style="display: inline-block;" class="modal-title">${filename}</h2>
            <span class="w3-btn w3-circle w3-display-topright close" style="color: black; font-size: 1.5em; font-weight: bold;">&times;</span>
        </header>
        <div class="w3-container w3-leftbar w3-border-green code-modal-body code-div"></div>
        <footer class="w3-container w3-display-bottomright">
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge copy">Copy</button>
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge download">Download</button>
            <button class="w3-btn w3-white w3-border w3-border-green w3-round-xlarge print">Print</button>
            <button class="w3-btn w3-white w3-border w3-border-red w3-round-xlarge close">Close</button>
        </footer>
    </div>
</div>`
}

class DKFileList {
    /**
     * document.querySelector(targetQuery) 항목을 찾아 파일 목록들로 채운다.
     *
     * @param {String} lsResultPath "ls -R" 결과와 같은 포맷의 텍스트 파일 경로
     * @param {Function} fileAction Optional. 파일 클릭 시, 디렉터리 경로와 파일명을 먹는 임의 함수. 기본값: 새 탭에서 열기
     * @param {Boolean} openAll Optional. 목록을 처음부터 모두 열 것인지 여부. 기본값: false
     * @param {Function} fileHTMLmaker Optional. 디렉터리 경로와 파일명을 먹고 HTMLElement를 반환하는 함수. 기본값: li 요소 생성
     * @param {Boolean} sort Optional. 목록을 정렬할 지 여부. 기본값: true
     * @param {Function} callback Optional. DKFileList => ?
     */
    constructor(lsResultPath, targetQuery, fileAction, openAll, fileHTMLmaker, sort, callback) {
        this.target = document.querySelector(targetQuery)
        this.fileAction = (!!fileAction) ? fileAction : null
        this.openAll = !!openAll
        this.fileHTMLmaker = (!!fileHTMLmaker) ? fileHTMLmaker : null
        this.sort = !!sort
        this.target.innerHTML = ''
        this.fileMap = new Map()
        this.rootDir = null
        this.callback = callback

        if (!this.target)
            return
        let xhr = new XMLHttpRequest()
        xhr.addEventListener('load', ((fileList) => function (e) {
            if (this.status != 200) {
                fileList.target.innerHTML = 'No Data'
                return
            }
            let dir = null
            for (let line of this.responseText.replace(/\r/gm, '').split('\n')) {
                if (line.endsWith(':')) {
                    dir = line.slice(0, -1)
                    if (!fileList.rootDir)
                        fileList.rootDir = dir
                    fileList.fileMap.set(dir, [])
                } else if (line.length > 0)
                    fileList.fileMap.get(dir).push(line)
            }
            if (this.sort)
                for (let files of fileList.fileMap.values())
                    files.sort(SFUtil.compareString)
            //console.log(fileList.fileMap)
            fileList.updateFileList(fileList.rootDir)
            fileList.callback && fileList.callback(this)
        })(this))
        xhr.open('GET', lsResultPath, true)
        xhr.send()
    }

    updateFileList(dir) {
        let details = document.getElementById(`dir-${dir.hashCode()}`)
        if (dir == this.rootDir && !details) {
            this.target.append(DKFileList.getDirHTML(dir, '', true))
            this.updateFileList(dir)
            return
        }

        let ul = details.querySelector('ul')
        if (ul.childElementCount > 0)
            return

        for (let name of this.fileMap.get(dir)) {
            let path = `${dir}/${name}`
            let isDir = this.fileMap.has(path)
            if (isDir) {
                while (this.fileMap.has(path)
                    && this.fileMap.get(path).length == 1
                    && this.fileMap.has(`${path}/${this.fileMap.get(path)[0]}`))
                    path = `${path}/${this.fileMap.get(path)[0]}`
                if (this.fileMap.get(path).length < 1)
                    continue
                ul.append(DKFileList.getDirHTML(path, dir, this.openAll))
                let dirAction = ((fileList, dir) => function (e) { fileList.updateFileList(dir); })(this, path)
                document.getElementById(`dir-${path.hashCode()}`).firstChild.onclick = dirAction
                if (this.openAll)
                    this.updateFileList(path)
            } else {
                if (!!this.fileAction) {
                    let element = !!this.fileHTMLmaker ? this.fileHTMLmaker(dir, name) : DKFileList.getFileHTMLwithoutA(dir, name)
                    ul.append(element)
                    let fileAction = ((fileList, dir, name) => function (e) { fileList.fileAction(dir, name); })(this, dir, name)
                    element.onclick = fileAction
                } else
                    ul.append(!!this.fileHTMLmaker ? this.fileHTMLmaker(dir, name) : DKFileList.getFileHTMLwithA(this.rootDir, dir, name))
            }
        }
    }

    static getDirHTML(dir, parentDir, open) {
        return `<details ${(!!open) ? 'open' : ''} id="dir-${dir.hashCode()}" class="w3-small file-list" title="${dir}"><summary>${dir.replace(parentDir, '')}</summary><ul></ul></details>`.asSF().$
    }

    static getFileHTMLwithA(rootDir, dir, name) {
        let path = `${dir.substr(rootDir.length)}/${name}`
        return `<li><a href="${path}">${name}</a></li>`.asSF().$
    }

    static getFileHTMLwithoutA(dir, name) {
        let path = `${dir}/${name}`
        return `<li title="${path}">${name}</li>`.asSF().$
    }
}
