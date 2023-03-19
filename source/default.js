const posts = {
    list: [
        { category: 'Algorithm', file: '/posts/algorithm/acmicpc.html', title: 'ACM-ICPC 문제' },
        { category: ['Algorithm', 'Topic/Book'], file: '/posts/book/041.html', title: 'Head First Data Analysis' },
        { category: ['Algorithm', 'Topic/Book'], file: '/posts/algorithm/elementary_mathematics.html', title: '기초 수학' },
        { category: 'Algorithm', file: '/posts/algorithm/linear_algebra.html', title: '선형대수' },
        { category: 'Algorithm', file: '/posts/algorithm/overview.html', title: '알고리즘 개요' },
        { category: ['Algorithm', 'Topic/Book'], file: '/posts/algorithm/mcs.html', title: '컴퓨터공학도를 위한 수학' },
        { category: 'Algorithm', file: '/posts/algorithm/probability.html', title: '확률' },

        { category: 'Infra', file: '/posts/infra/aws.html', title: 'AWS' },
        { category: 'Infra', file: '/posts/infra/docker.html', title: 'Container' },
        { category: 'Infra', file: '/posts/infra/git.html', title: 'Git' },
        { category: 'Infra', file: '/posts/infra/gradle.html', title: 'Gradle' },
        { category: 'Infra', file: '/posts/infra/heroku.html', title: 'Heroku' },
        { category: 'Infra', file: '/posts/infra/linux.html', title: 'Linux' },
        { category: 'Infra', file: '/posts/infra/nginx.html', title: 'Nginx' },
        { category: 'Infra', file: '/posts/infra/sdkman.html', title: 'SDKMAN; The Software Development Kit Manager' },
        { category: ['Infra', 'Topic/Book'], file: '/posts/book/017.html', title: '인프라 디자인' },

        { category: 'Infra/DB', file: '/posts/infra/db/concept.html', title: 'DB 기초 개념' },
        { category: 'Infra/DB', file: '/posts/infra/db/mongodb.html', title: 'MongoDB' },
        { category: 'Infra/DB', file: '/posts/infra/db/mysql.html', title: 'MySQL' },
        { category: 'Infra/DB', file: '/posts/infra/db/psql_admin.html', title: 'PostgreSQL 서버 관리' },
        { category: 'Infra/DB', file: '/posts/infra/db/psql_programming.html', title: 'PostgreSQL 서버 프로그래밍' },
        { category: 'Infra/DB', file: '/posts/infra/db/psql_sql.html', title: 'PostgreSQL SQL 언어' },
        { category: 'Infra/DB', file: '/posts/infra/db/psql_tutorial.html', title: 'PostgreSQL 시작하기' },
        { category: 'Infra/DB', file: '/posts/infra/db/psql_to_sqlite.html', title: 'PostgreSQL → SQLite3 마이그레이션' },
        { category: 'Infra/DB', file: '/posts/infra/db/RabbitMQ.html', title: 'RabbitMQ' },
        { category: 'Infra/DB', file: '/posts/infra/db/redis.html', title: 'Redis 6' },

        { category: 'Infra/Web', file: '/posts/infra/web/css.html', title: 'CSS' },
        { category: 'Infra/Web', file: '/posts/infra/web/html.html', title: 'HTML' },
        { category: 'Infra/Web', file: '/posts/infra/web/network.html', title: 'Network' },
        { category: 'Infra/Web', file: '/posts/infra/web/oauth.html', title: 'OAuth' },

        { category: 'Language/.NET', file: '/posts/language/.net/csharp_basic.html', title: 'C#.NET' },
        { category: 'Language/.NET', file: '/posts/language/.net/wpf_basic.html', title: 'WPF' },
        { category: 'Language/JavaScript', file: '/posts/language/javascript/basic.html', title: '코어 JavaScript' },
        { category: 'Language/JavaScript', file: '/posts/language/javascript/basic2.html', title: '브라우저 JavaScript' },
        { category: 'Language/JavaScript', file: '/posts/language/javascript/jquery.html', title: 'jQuery' },
        { category: 'Language/JavaScript', file: '/posts/language/javascript/node.html', title: 'Node' },
        { category: 'Language/JVM', file: '/posts/language/jvm/android.html', title: 'Android' },
        { category: 'Language/JVM', file: '/posts/language/jvm/basic.html', title: 'Java 시작하기' },
        { category: ['Language/JVM', 'Topic/Book'], file: '/posts/language/jvm/effective_java.html', title: 'Effective Java' },
        { category: 'Language/JVM', file: '/posts/language/jvm/groovy.html', title: 'Groovy' },
        { category: 'Language/JVM', file: '/posts/language/jvm/javax.annotation.processing.html', title: 'Annotation Processing API' },
        { category: 'Language/JVM', file: '/posts/language/jvm/jni.html', title: 'Java Native Interface' },
        { category: 'Language/JVM', file: '/posts/language/jvm/performance.html', title: 'JVM 옵션 및 성능 관련사항' },
        { category: 'Language/JVM', file: '/posts/language/jvm/version.html', title: 'Java 버전별 추가사항' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/apache.commons.collections.html', title: 'Apache Commons Collections 4.4' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/apache.commons.lang.html', title: 'Apache Commons Lang 3.9' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/apache.commons.math.html', title: 'Apache Commons Math 3.6.1' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/apache.commons.rng.html', title: 'Apache Commons RNG Core 1.2' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/com.sun.nio.sctp.html', title: 'com.sun.nio.sctp Since 1.7' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/freemarker_built_in.html', title: 'FreeMarker Built-in 목록 2.3.28' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/freemarker_programming_guide.html', title: 'FreeMarker 프로그래밍 가이드 2.3.28' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/freemarker_template_guide.html', title: 'FreeMarker 템플릿 작성 가이드 2.3.28' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/freemarker_xml_guide.html', title: 'FreeMarker XML 처리 가이드 2.3.28' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/gson.html', title: 'Gson 2.8.6' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/guava.html', title: 'Guava 30.1' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/jackson.html', title: 'Jackson 2.10.1' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/java.base.html', title: 'java.base 16' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/java.net.http.html', title: 'java.net.http 16' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/logback.html', title: 'Logback with SLF4J' },
        { category: 'Language/JVM/Library', file: '/posts/language/jvm/lombok.html', title: 'lombok 1.18.10' },
        { category: 'Language/JVM/Web', file: '/posts/language/jvm/java_ee.html', title: 'JavaEE' },
        { category: 'Language/JVM/Web', file: '/posts/language/jvm/jpa.html', title: 'JPA; Java Persistence API' },
        { category: 'Language/JVM/Web', file: '/posts/language/jvm/netty.html', title: 'Netty' },
        { category: 'Language/JVM/Web', file: '/posts/language/jvm/spring_framework.html', title: 'Spring Framework' },
        { category: 'Language/JVM/Web', file: '/posts/language/jvm/spring_reactive.html', title: 'Web Reactive' },
        { category: 'Language/JVM/Web', file: '/posts/language/jvm/spring_servlet.html', title: 'Web Servlet' },
        { category: 'Language/Python', file: '/posts/language/python/basic.html', title: 'Python 3.8 시작하기' },
        { category: 'Language/Python', file: '/posts/language/python/built_in_constant.html', title: 'Python Built-in Constants 3.8' },
        { category: 'Language/Python', file: '/posts/language/python/built_in_exception.html', title: 'Python Built-in Exceptions 3.8' },
        { category: 'Language/Python', file: '/posts/language/python/built_in_function.html', title: 'Python Built-in Functions 3.8' },
        { category: 'Language/Python', file: '/posts/language/python/built_in_type.html', title: 'Python Built-in Types 3.8' },
        { category: 'Language/Python', file: '/posts/language/python/data_model.html', title: 'Python 3.8 데이터 모델' },
        { category: 'Language/Python', file: '/posts/language/python/persistence.html', title: 'Python Data Persistence' },
        { category: 'Language/Python/Library', file: '/posts/language/python/pandas.html', title: 'pandas : 데이터 처리' },
        { category: 'Language/Python/Library', file: '/posts/language/python/standard.html', title: '표준 라이브러리' },

        { category: 'Project', file: '/posts/project/cctv.html', title: 'Android CCTV = Telegram Bot + YouTube' },
        { category: 'Project', file: '/posts/project/error.html', title: '예외/오류 해결' },
        { category: 'Project', file: '/posts/project/ip2country.html', title: 'IPv4 to Country' },
        { category: 'Project', file: '/posts/project/snippets.html', title: '코드 조각' },
        { category: 'Project', file: '/posts/project/tip.html', title: '잡다한 팁' },

        { category: 'Topic', file: '/posts/topic/docker_mailserver.html', title: 'Docker Mail Server 설치' },
        { category: 'Topic', file: '/posts/topic/fp.html', title: 'FP; Functional Programming' },
        { category: 'Topic', file: '/posts/topic/frp.html', title: 'frp 설치' },
        { category: 'Topic', file: '/posts/topic/interview.html', title: '개발자 경력 관리' },
        { category: 'Topic', file: '/posts/topic/refactoring.html', title: '디자인 패턴 + 리팩터링' },
        { category: 'Topic', file: '/posts/topic/rpi.html', title: 'Raspberry Pi 3 + OpenCV(python)' },
        { category: ['Topic', 'Topic/Book'], file: '/posts/book/010.html', title: '애자일 & 스크럼 프로젝트 관리' },
        { category: ['Topic', 'Topic/Book'], file: '/posts/book/015.html', title: '컴퓨터 시스템' },

        { category: 'Topic/Book', file: '/posts/book/001.html', title: '1만 시간의 재발견' },
        { category: 'Topic/Book', file: '/posts/book/002.html', title: '마음의 탄생' },
        { category: 'Topic/Book', file: '/posts/book/003.html', title: '생각하는 삶을 위한 철학의 역사' },
        { category: 'Topic/Book', file: '/posts/book/004.html', title: '쇼펜하우어, 돌이 별이 되는 철학' },
        { category: 'Topic/Book', file: '/posts/book/005.html', title: '인생의 모든 의미' },
        { category: 'Topic/Book', file: '/posts/book/006.html', title: '지식의 착각' },
        { category: 'Topic/Book', file: '/posts/book/007.html', title: '키르케고르 실존 극장' },
        { category: 'Topic/Book', file: '/posts/book/008.html', title: 'Gamification by Design' },
        { category: 'Topic/Book', file: '/posts/book/009.html', title: '인지편향사전' },
        { category: 'Topic/Book', file: '/posts/book/011.html', title: '나는 뇌가 아니다' },
        { category: 'Topic/Book', file: '/posts/book/012.html', title: '우리는 어떻게 바뀌고 있는가' },
        { category: 'Topic/Book', file: '/posts/book/013.html', title: '위험한 생각들' },
        { category: 'Topic/Book', file: '/posts/book/014.html', title: '어이없는 진화' },
        { category: 'Topic/Book', file: '/posts/book/016.html', title: '물 위를 걷고 벽을 기어오르는 법' },
        { category: 'Topic/Book', file: '/posts/book/018.html', title: '쇠똥구리는 은하수를 따라 걷는다' },
        { category: 'Topic/Book', file: '/posts/book/019.html', title: '아름다움의 진화' },
        { category: 'Topic/Book', file: '/posts/book/020.html', title: '거의 모든 것의 기원' },
        { category: 'Topic/Book', file: '/posts/book/021.html', title: '배드 사이언스' },
        { category: 'Topic/Book', file: '/posts/book/022.html', title: '나를 나답게 만드는 것들' },
        { category: 'Topic/Book', file: '/posts/book/023.html', title: '로스트 케어' },
        { category: 'Topic/Book', file: '/posts/book/024.html', title: '안녕, 긴 잠이여' },
        { category: 'Topic/Book', file: '/posts/book/025.html', title: '천사들의 탐정' },
        { category: 'Topic/Book', file: '/posts/book/026.html', title: '지금부터의 내일' },
        { category: 'Topic/Book', file: '/posts/book/027.html', title: '괴도 탐정 야마네코' },
        { category: 'Topic/Book', file: '/posts/book/028.html', title: '괴물의 야회' },
        { category: 'Topic/Book', file: '/posts/book/029.html', title: '데드맨' },
        { category: 'Topic/Book', file: '/posts/book/030.html', title: '단델라이언' },
        { category: 'Topic/Book', file: '/posts/book/031.html', title: '내가 죽인 소녀' },
        { category: 'Topic/Book', file: '/posts/book/032.html', title: '그리고 밤은 되살아난다' },
        { category: 'Topic/Book', file: '/posts/book/033.html', title: '저지먼트' },
        { category: 'Topic/Book', file: '/posts/book/034.html', title: '검은 집' },
        { category: 'Topic/Book', file: '/posts/book/035.html', title: '연쇄 살인마 개구리 남자' },
        { category: 'Topic/Book', file: '/posts/book/036.html', title: '도깨비불의 집' },
        { category: 'Topic/Book', file: '/posts/book/037.html', title: '연쇄 살인마 개구리 남자의 귀환' },
        { category: 'Topic/Book', file: '/posts/book/038.html', title: '국경' },
        { category: 'Topic/Book', file: '/posts/book/039.html', title: '악당' },
        { category: 'Topic/Book', file: '/posts/book/040.html', title: '보호받지 못한 사람들' },
        { category: 'Topic/Book', file: '/posts/book/042.html', title: '시즈카 할머니와 은령 탐정사' },
        { category: 'Topic/Book', file: '/posts/book/043.html', title: '옆방에 킬러가 산다' },
        { category: 'Topic/Book', file: '/posts/book/044.html', title: '시즈카 할머니와 휠체어 탐정' },
        { category: 'Topic/Book', file: '/posts/book/045.html', title: '시즈카 할머니에게 맡겨줘' },
        { category: 'Topic/Book', file: '/posts/book/046.html', title: '비웃는 숙녀' },

        { category: 'Topic/Life', file: '/posts/daily_life/business.html', title: '개인사업자' },
        { category: 'Topic/Life', file: '/posts/daily_life/car.html', title: '차' },
        { category: 'Topic/Life', file: '/posts/daily_life/death.html', title: '장례' },
        { category: 'Topic/Life', file: '/posts/daily_life/house.html', title: '부동산' },
        { category: 'Topic/Life', file: '/posts/daily_life/labor.html', title: '근로' },
        { category: 'Topic/Life', file: '/posts/daily_life/stock.html', title: '주식' },
    ], codes: {}
}

posts.list = posts.list.filter(post => !Array.isArray(post.category)).concat(
    posts.list.filter(post => Array.isArray(post.category)).flatMap(post => {
        let arr = []
        for (let category of post.category)
            arr.push(Object.assign({}, post, { category: category }))
        return arr
    }))
posts.list.sort((post1, post2) => SFUtil.compareString(post1.title, post2.title))
posts.list.sort((post1, post2) => SFUtil.compareString(post1.category, post2.category))

window.addEventListener('load', () => {
    new MutationObserver(mutationCallback).observe(document.body, { attributes: false, childList: true, subtree: true })
    mutationCallback([{ type: 'childList', target: document.body }])

    /* 하이라이팅 지원 목록 */
    console.log(hljs.listLanguages())

    updateSidebar()
    updatePostList()
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
            localStorage.setItem('gotoEvent', true)
            setTimeout(() => localStorage.removeItem('gotoEvent'), 1000)
        })
    }
    window.onpopstate = function (e) {
        if (!localStorage.getItem('gotoEvent') && localStorage.getItem(`${location.href}-lastPos`)) {
            window.scrollTo({ top: localStorage.getItem(`${location.href}-lastPos`) })
            return
        }

        let pos = /#(pos-?\d+)/.exec(location.hash)
        if (pos) {
            let target = document.getElementById(pos[1])
            let parent = target
            while (parent.tagName != 'BODY') {
                if (parent.tagName == 'DETAILS')
                    parent.open = true
                parent = parent.parentElement
            }
            while (!target.clientHeight) {
                if (target.nextSibling && target.nextSibling.clientHeight) {
                    target = target.nextSibling
                    break
                }
                target = target.parentElement
            }
            goto(target)
        }
    }

    if (Array.isArray(window.wizFuncQueue))
        for (let func of window.wizFuncQueue)
            try { func() } catch (e) { console.log(e) }
})

function goto(target) {
    SFUtil.highlight(target)
    let arg = {
        top: SFUtil.getOffsetTop(target) - document.getElementById('nav').clientHeight
    }
    setTimeout(() => window.scrollTo(arg), 100)
}

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
        div.style.maxHeight = window.innerHeight / 3 + 'px'
        div.classList.remove('as-code')
    }
}

function isNarrow() {
    return document.getElementById('nav').clientWidth <= 1234
}

function updateSidebar() {
    if (isNarrow())
        closeSidebar()
    else
        openSidebar()
    document.getElementById('sidebar').style.width = '333px'
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
    let url = URL.createObjectURL(new Blob([SFUtil.makeLSlikeText('Category', categoryMap, 'posts')], {
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

function updateMarkerList() {
    for (let h of document.querySelectorAll('h1,h2,h3,h4,h5,h6'))
        h.classList.add('marker')

    let markerMap = { markers: [] }
    let id = new Date().getTime()
    for (let marker of document.querySelectorAll('.marker')) {
        marker.setAttribute('marker-id', `marker-${id++}`)
        markerMap.markers.push(marker.getAttribute('marker-id'))
    }
    let url = URL.createObjectURL(new Blob([SFUtil.makeLSlikeText('Content', markerMap, 'markers')], {
        type: 'text/plain;charset=utf-8;'
    }))
    new DKFileList(url, '#marker-list', (_, markerId) => {
        let target = document.querySelector(`.marker[marker-id=${markerId}]`)
        let parent = target
        while (parent.tagName != 'BODY') {
            if (parent.tagName == 'DETAILS')
                parent.open = true
            parent = parent.parentElement
        }
        goto(target)
    }, true, (_, markerId) => {
        let target = document.querySelector(`.marker[marker-id=${markerId}]`)
        let name = getMarkerName(target)
        let li = `<li class="${target.classList.contains('fake') ? 'w3-hide' : ''}" title="${name}" marker-id="${markerId}"></li>`.asSF().$;
        li.textContent = name.substr(0, 50);

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
    return name
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
                let div = `<div id="code-div-${id}" class="w3-leftbar w3-border-green code-div"></div>`.asSF().$
                let lan = button.getAttribute('lan')
                if (this.status != 200)
                    this.responseText = 'Ajax Failed'

                posts.codes[id] = this.responseText
                fillCodeDiv(div, lan, this.responseText, button.getAttribute('displayRange'))
                div.style.maxHeight = window.innerHeight / 3 + 'px'

                if (lan != 'nohighlight') {
                    let modal = '<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-blue">모달로 보기</button>'.asSF().$
                    modal.onclick = showModal(id)
                    button.after(modal)
                    modal.after(div)
                } else
                    button.after(div)

                if (lan == 'javascript') {
                    let script = '<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-green">실행</button>'.asSF().$
                    script.onclick = ((id) => function (e) {
                        let code = Array.from(document.querySelectorAll(`#code-div-${id} li`)).map(li => li.textContent).join('\n')
                        eval(code)
                    })(id)
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
            div.style.maxHeight = window.innerHeight / 3 + 'px'
        }
    }
}

function fillCodeDiv(div, lan, text, displayRange) {
    if (lan != 'nohighlight') {
        let code = text.replace(/\t/gm, '    ')
        code = code.replace(/ /gm, '  ')

        let lines = code.split(/\n/gm)
        let displayParts = []
        displayRange = JSON.parse(displayRange || '[1, 100000000]') || [1, 100000000]
        if (displayRange.length % 2 == 1)
            displayRange.push(100000000)
        displayRange = displayRange.reverse()

        while (displayRange.length > 0) {
            let displayLines = []
            let displayStart = displayRange.pop() - 1
            let displayEnd = displayRange.pop()
            for (let idx = displayStart; idx < displayEnd && idx < lines.length; ++idx)
                displayLines.push(lines[idx])

            let commonBlankSize = 0
            for (let checkIdx = 0; checkIdx < displayLines[0].length; ++checkIdx) {
                let isContinue = true
                for (let line of displayLines) {
                    if (/\S/.test(line.charAt(checkIdx)))
                        isContinue = false
                    if (!isContinue)
                        break
                }
                if (!isContinue)
                    break
                commonBlankSize++
            }

            if (commonBlankSize > 0) {
                for (let idx = 0; idx < displayLines.length; ++idx)
                    displayLines[idx] = displayLines[idx].substr(commonBlankSize)
            }

            if (lan != 'text')
                displayLines = hljs.highlight(lan, displayLines.join('\n'))['value'].split(/\n/gm)
            displayParts.push(displayLines)
        }

        let ol = document.createElement('ol')
        for (let displayLines of displayParts) {
            for (let line of displayLines) {
                if (lan == 'text') {
                    let li = document.createElement('li')
                    li.innerText = line.replace(/  /gm, '\u00A0')
                    ol.append(li)
                } else
                    ol.append(`<li>${line.replace(/  /gm, '&nbsp;')}</li>`.asSF().$)
            }
            if (displayLines != displayParts[displayParts.length - 1])
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
