const posts = { list: [
{ file: '/posts/single/infra.html',                         category: '단일 문서',                       title: 'Infra Index' },
{ file: '/posts/single/aws.html',                           category: '단일 문서',                       title: 'AWS' },
{ file: '/posts/single/centos.html',                        category: '단일 문서',                       title: 'CentOS' },
{ file: '/posts/single/docker.html',                        category: '단일 문서',                       title: 'Docker' },
{ file: '/posts/single/fp.html',                            category: '단일 문서',                       title: 'FP; Functional Programming' },
{ file: '/posts/single/git.html',                           category: '단일 문서',                       title: 'Git' },
{ file: '/posts/single/gradle.html',                        category: '단일 문서',                       title: 'Gradle' },
{ file: '/posts/single/groovy.html',                        category: '단일 문서',                       title: 'Groovy' },
{ file: '/posts/single/nginx.html',                         category: '단일 문서',                       title: 'Nginx' },
{ file: '/posts/single/rpi.html',                           category: '단일 문서',                       title: 'Raspberry Pi 3 + OpenCV(python)' },
{ file: '/posts/single/redis.html',                         category: '단일 문서',                       title: 'Redis' },
{ file: '/posts/single/sdkman.html',                        category: '단일 문서',                       title: 'SDKMAN; The Software Development Kit Manager' },
{ file: '/posts/single/tomcat.html',                        category: '단일 문서',                       title: 'tomcat' },
{ file: '/posts/single/vi.html',                            category: '단일 문서',                       title: 'vi' },
{ file: '/posts/single/refactoring.html',                   category: '단일 문서',                       title: '디자인 패턴 + 리팩터링' },
{ file: '/posts/book/001.html',                             category: '독서',                           title: '1만 시간의 재발견' },
{ file: '/posts/book/002.html',                             category: '독서',                           title: '마음의 탄생' },
{ file: '/posts/book/003.html',                             category: '독서',                           title: '생각하는 삶을 위한 철학의 역사' },
{ file: '/posts/book/004.html',                             category: '독서',                           title: '쇼펜하우어, 돌이 별이 되는 철학' },
{ file: '/posts/book/005.html',                             category: '독서',                           title: '인생의 모든 의미' },
{ file: '/posts/book/006.html',                             category: '독서',                           title: '지식의 착각' },
{ file: '/posts/book/007.html',                             category: '독서',                           title: '키르케고르 실존 극장' },
{ file: '/posts/book/008.html',                             category: '독서',                           title: 'Gamification by Design' },
{ file: '/posts/book/009.html',                             category: '독서',                           title: '인지 편향' },
{ file: '/posts/algorithm/algo.html',                       category: '알고리즘',                        title: '알고리즘 일반' },
{ file: '/posts/algorithm/book01.html',                     category: '알고리즘',                        title: '『알고리즘 도감』' },
{ file: '/posts/algorithm/linear_algebra.html',             category: '알고리즘',                        title: '선형대수' },
{ file: '/posts/algorithm/probability.html',                category: '알고리즘',                        title: '확률' },
{ file: '/posts/algorithm/koreatech/1003.html',             category: '알고리즘/KOREATECH',              title: '1003: 0을 만들자' },
{ file: '/posts/algorithm/koreatech/1008.html',             category: '알고리즘/KOREATECH',              title: '1008: 순환 소수' },
{ file: '/posts/algorithm/koreatech/1010.html',             category: '알고리즘/KOREATECH',              title: '소수(Prime) 관련 문제' },
{ file: '/posts/algorithm/koreatech/1011.html',             category: '알고리즘/KOREATECH',              title: '동적계획법(DP) 문제' },
{ file: '/posts/algorithm/koreatech/1018.html',             category: '알고리즘/KOREATECH',              title: '1018: 문자열 거리 최소화 하기' },
{ file: '/posts/algorithm/koreatech/1034.html',             category: '알고리즘/KOREATECH',              title: '1034,1041: 최소 이동 거리' },
{ file: '/posts/algorithm/koreatech/1048.html',             category: '알고리즘/KOREATECH',              title: '1048: AP 배분' },
{ file: '/posts/algorithm/koreatech/1095.html',             category: '알고리즘/KOREATECH',              title: '1095: 자연스러운 정렬' },
{ file: '/posts/algorithm/ai.html',                         category: '작성 중지',                       title: 'AI' },
{ file: '/posts/single/quantum_computer.html',              category: '작성 중지',                       title: '양자 컴퓨터' },
{ file: '/posts/single/unity.html',                         category: '작성 중지',                       title: 'Unity' },
{ file: '/posts/db/index.html',                             category: 'DB',                             title: 'DB, Cache Index' },
{ file: '/posts/db/concept.html',                           category: 'DB',                             title: 'DB 기초' },
{ file: '/posts/db/mongodb.html',                           category: 'DB',                             title: 'MongoDB' },
{ file: '/posts/db/mysql.html',                             category: 'DB',                             title: 'MySQL' },
{ file: '/posts/db/psql_to_sqlite.html',                    category: 'DB',                             title: 'PostgreSQL → SQLite3 마이그레이션' },
{ file: '/posts/db/psql_admin.html',                        category: 'DB',                             title: 'PostgreSQL 서버 관리' },
{ file: '/posts/db/psql_programming.html',                  category: 'DB',                             title: 'PostgreSQL 서버 프로그래밍' },
{ file: '/posts/db/psql_tutorial.html',                     category: 'DB',                             title: 'PostgreSQL 시작하기' },
{ file: '/posts/db/psql_sql.html',                          category: 'DB',                             title: 'PostgreSQL SQL 언어' },
{ file: '/posts/dotnet/index.html',                         category: 'Programming/.NET',               title: '.NET Index' },
{ file: '/posts/dotnet/csharp_basic.html',                  category: 'Programming/.NET',               title: 'C# 기초' },
{ file: '/posts/dotnet/csharp_library.html',                category: 'Programming/.NET',               title: 'C# 라이브러리' },
{ file: '/posts/dotnet/wpf_basic.html',                     category: 'Programming/.NET',               title: 'WPF 기초' },
{ file: '/posts/front/css.html',                            category: 'Programming/Front',              title: 'CSS' },
{ file: '/posts/front/html.html',                           category: 'Programming/Front',              title: 'HTML' },
{ file: '/posts/front/http.html',                           category: 'Programming/Front',              title: 'HTTP 1.1' },
{ file: '/posts/front/freemarker/built_in.html',            category: 'Programming/FreeMarker',         title: 'Built-in 목록 2.3.28' },
{ file: '/posts/front/freemarker/programming_guide.html',   category: 'Programming/FreeMarker',         title: '프로그래밍 가이드 2.3.28' },
{ file: '/posts/front/freemarker/template_guide.html',      category: 'Programming/FreeMarker',         title: '템플릿 작성 가이드 2.3.28' },
{ file: '/posts/front/freemarker/xml_guide.html',           category: 'Programming/FreeMarker',         title: 'XML 처리 가이드 2.3.28' },
{ file: '/posts/java/index.html',                           category: 'Programming/Java',               title: 'Java Index' },
{ file: '/posts/java/android.html',                         category: 'Programming/Java',               title: 'Android' },
{ file: '/posts/java/basic.html',                           category: 'Programming/Java',               title: 'Java 시작하기' },
{ file: '/posts/java/effective_java.html',                  category: 'Programming/Java',               title: '『Effective Java』' },
{ file: '/posts/java/java_ee.html',                         category: 'Programming/Java',               title: 'JavaEE' },
{ file: '/posts/java/javafx.html',                          category: 'Programming/Java',               title: 'JavaFX' },
{ file: '/posts/java/jni.html',                             category: 'Programming/Java',               title: 'Java Native Interface' },
{ file: '/posts/java/oop.html',                             category: 'Programming/Java',               title: 'Java 객체지향' },
{ file: '/posts/java/version.html',                         category: 'Programming/Java',               title: 'Java 버전별 추가사항' },
{ file: '/posts/java/performance.html',                     category: 'Programming/Java',               title: 'JVM 옵션 및 성능 관련사항' },            // 미완
{ file: '/posts/java/apache.commons.collections.html',      category: 'Programming/Java/3rd',           title: 'Apache Commons Collections 4.4' },
{ file: '/posts/java/apache.commons.lang.html',             category: 'Programming/Java/3rd',           title: 'Apache Commons Lang 3.9' },
{ file: '/posts/java/apache.commons.math.html',             category: 'Programming/Java/3rd',           title: 'Apache Commons Math 3.6.1' },         // 미완
{ file: '/posts/java/apache.commons.rng.html',              category: 'Programming/Java/3rd',           title: 'Apache Commons RNG Core 1.2' },
{ file: '/posts/java/jackson.html',                         category: 'Programming/Java/3rd',           title: 'Jackson 2.10.1' },
{ file: '/posts/java/gson.html',                            category: 'Programming/Java/3rd',           title: 'Gson 2.8.6' },
{ file: '/posts/java/guava.html',                           category: 'Programming/Java/3rd',           title: 'Guava 23.0' },                        // 미완
{ file: '/posts/java/lombok.html',                          category: 'Programming/Java/3rd',           title: 'lombok 1.18.10' },
{ file: '/posts/java/logback.html',                         category: 'Programming/Java/3rd',           title: 'Logback with SLF4J' },
{ file: '/posts/java/java.base.html',                       category: 'Programming/Java/Standard',      title: 'java.base 12' },
{ file: '/posts/java/javax.annotation.processing.html',     category: 'Programming/Java/Standard',      title: 'Annotation Processing API' },
{ file: '/posts/java/com.sun.nio.sctp.html',                category: 'Programming/Java/Standard',      title: 'com.sun.nio.sctp Since 1.7' },
{ file: '/posts/javascript/index.html',                     category: 'Programming/JavaScript',         title: 'JavaScript Index' },
{ file: '/posts/javascript/basic.html',                     category: 'Programming/JavaScript',         title: '코어 JavaScript' },
{ file: '/posts/javascript/basic2.html',                    category: 'Programming/JavaScript',         title: '브라우저 JavaScript' },
{ file: '/posts/javascript/jquery.html',                    category: 'Programming/JavaScript',         title: 'jQuery' },
{ file: '/posts/javascript/node.html',                      category: 'Programming/JavaScript',         title: 'Node' },
{ file: '/posts/python/index.html',                         category: 'Programming/Python',             title: 'Python Index' },
{ file: '/posts/python/basic.html',                         category: 'Programming/Python',             title: 'Python 3.8 시작하기' },
{ file: '/posts/python/built_in_constant.html',             category: 'Programming/Python',             title: 'Python Built-in Constants 3.8' },
{ file: '/posts/python/built_in_exception.html',            category: 'Programming/Python',             title: 'Python Built-in Exceptions 3.8' },
{ file: '/posts/python/built_in_function.html',             category: 'Programming/Python',             title: 'Python Built-in Functions 3.8' },
{ file: '/posts/python/built_in_type.html',                 category: 'Programming/Python',             title: 'Python Built-in Types 3.8' },
{ file: '/posts/python/data_model.html',                    category: 'Programming/Python',             title: 'Python 3.8 데이터 모델' },
{ file: '/posts/python/standard.html',                      category: 'Programming/Python',             title: '표준 라이브러리' },
{ file: '/posts/python/persistence.html',                   category: 'Programming/Python',             title: 'Python Data Persistence' },
{ file: '/posts/python/pandas.html',                        category: 'Programming/Python',             title: '3rd/pandas : 데이터 처리' },
{ file: '/posts/project/error.html',                        category: 'Project',                        title: 'Exceptions + Errors' },
{ file: '/posts/project/tip.html',                          category: 'Project',                        title: '잡다한 팁' },
{ file: '/posts/project/snippets.html',                     category: 'Project',                        title: 'Code Snippets' },
{ file: '/posts/project/sf.html',                           category: 'Project',                        title: 'SF; Simplest html Framework' },
{ file: '/posts/project/wpf_data_tool.html',                category: 'Project',                        title: 'C#.NET, WPF - 데이터 작업 툴' },
], codes: {} }

window.addEventListener('load', () => {
    new MutationObserver(mutationCallback).observe(document.body, { attributes: false, childList: true, subtree: true });
    mutationCallback([{ type: 'childList', target: document.body }]);

    /* 하이라이팅 지원 목록 */
    console.log(hljs.listLanguages());

    updateSidebar();
    updatePostList();
    insertDisqusThread();
    updateMarkerList();
    document.getElementById('query').onkeyup = SF.debounce(queryUpdated, 500);
});

function mutationCallback(mutations, observer) {
    for (let mutation of mutations) {
        if (mutation.type !== 'childList') return;
        addImageOnclick(mutation.target.querySelectorAll('img'));
        addCodeBtnOnclick(mutation.target.querySelectorAll('button.btn-code'));
        addHoverContents(mutation.target.querySelectorAll('.hover-content'));
        convertAsCodeDiv(mutation.target.querySelectorAll('.as-code'));
    }
}

function addImageOnclick(imgs) {
    for (let img of imgs) {
        if (!!img.onclick)
            continue;
        img.onclick = ((src) => function(e) { Donggi.openLink(src, '_blank'); })(img.src);
    }
}

function addCodeBtnOnclick(btns) {
    for (let button of btns) {
        let id = `${new Date().getTime()}-${Math.random().toString().replace(/\./g, '')}`;
        button.id = `code-button-${id}`;
        button.classList.remove('btn-code');
        button.onclick = insertCodeDiv(id);
    }
}

function addHoverContents(targets) {
    for (let hoverContent of targets)
        Donggi.addHoverContent(hoverContent, document.getElementById(hoverContent.getAttribute('template-id')));
}

function convertAsCodeDiv(divs) {
    for (let div of divs) {
        let code = div.innerHTML.trim().replace(/&amp;/gm, '&').replace(/&lt;/gm, '<').replace(/&gt;/gm, '>');
        // console.log(code);
        div.innerHTML = '';
        fillCodeDiv(div, div.getAttribute('lan'), code);
        div.style.maxHeight = window.innerHeight / 2;
        div.classList.remove('as-code');
    }
}

function isNarrow() {
    return document.getElementById('nav').clientWidth <= 600;
}

function updateSidebar() {
    if (isNarrow())
        closeSidebar();
    else
        openSidebar();
    document.getElementById('sidebar').style.width = '333px';
    new DKFileList('/source/filelist.js', '#file-list', null, false, null, true, () => {
        document.querySelector('#file-list>details').open = false;
    });
}

function openSidebar() {
    document.getElementById('main').style.marginLeft = '333px';
    document.getElementById('sidebar').style.display = 'block';
}

function closeSidebar() {
    document.getElementById('main').style.marginLeft = '0';
    document.getElementById('sidebar').style.display = 'none';
}

function toggleSidebar() {
    if (document.getElementById('sidebar').style.display == 'none')
        openSidebar();
    else
        closeSidebar();
}

function updatePostList() {
    posts.list.sort((post1, post2) => Donggi.compareString(post1.title, post2.title));
    posts.list.sort((post1, post2) => Donggi.compareString(post1.category, post2.category));

    let categoryMap = { posts: [] };
    for (let post of posts.list) {
        let category = categoryMap;
        for (let cate of post.category.split('/')) {
            if (!category.hasOwnProperty(cate)) {
                category[cate] = {};
                category[cate].posts = [];
            }
            category = category[cate];
        }
        category.posts.push(post.title);
    }
    let url = URL.createObjectURL(new Blob([Donggi.makeLSlikeText('카테고리', categoryMap, 'posts')], {
        type: 'text/plain;charset=utf-8;'
    }));
    new DKFileList(url, '#post-list', null, true, (category, title) => {
        let post = posts.list.filter(x => x.title == title)[0];
        return Donggi.getElementFromText(`<li><a href="${post.file}">${title}</a></li>`);
    }, true, () => {
        document.querySelectorAll('#post-list details').forEach(x => x.open = false);
        let limit = document.getElementById('post-list');
        let a = document.querySelector(`#post-list a[href="${location.pathname}"]`);
        if (a) {
            let p = a.parentElement;
            p.classList.add('w3-yellow');
            while (limit != p) {
                if (p.tagName == 'DETAILS')
                    p.open = true;
                p = p.parentElement;
            }
        } else {
            document.querySelector('#post-list details').open = true;
        }
    });
}

function insertDisqusThread() {
    let parent = document.querySelector('div#contents')
    if (!/dong-gi\.github\.io/i.test(location.host))
        return;

    if (!!document.querySelector('div#disqus_thread')) {
        DISQUS.reset({
            reload: true,
            config: function () {
                this.page.url = `https://dong-gi.github.io${location.pathname}`;
                this.page.identifier = location.pathname;
            }
        });
        parent.append(document.querySelector('div#disqus_thread'));
        return;
    }

    parent.append(Donggi.getElementFromText('<div id="disqus_thread"></div>'));
    eval(`var disqus_config = function () {
            this.page.url = 'https://dong-gi.github.io${location.pathname}';
            this.page.identifier = '${location.pathname}';
        };
        (function() {
            var d = document, s = d.createElement('script');
            s.src = 'https://donggi.disqus.com/embed.js';
            s.setAttribute('data-timestamp', +new Date());
            document.querySelector('div#disqus_thread').append(s);
        })();`);
}

function updateMarkerList() {
    let markerMap = { markers: [] };
    let id = new Date().getTime();
    for (let marker of document.querySelectorAll('.marker')) {
        marker.setAttribute('marker-id', `marker-${id++}`);
        markerMap.markers.push(marker.getAttribute('marker-id'));
    }
    let url = URL.createObjectURL(new Blob([Donggi.makeLSlikeText('컨텐츠', markerMap, 'markers')], {
        type: 'text/plain;charset=utf-8;'
    }));
    new DKFileList(url, '#marker-list', (_, markerId) => {
        if (isNarrow())
            closeSidebar();
        let target = document.querySelector(`.marker[marker-id=${markerId}]`);
        let parent = target;
        while (parent.tagName != 'BODY') {
            if (parent.tagName == 'DETAILS' && !parent.open)
                parent.querySelector('summary').click();
            parent = parent.parentElement;
        }
        target.style.animation = '';
        setTimeout(((target) => function () {
            target.style.animation = 'highlight 2s 1';
        })(target), 139);
        setTimeout(((target) => function () {
            window.scrollTo({
                top: target.offsetTop - document.getElementById('nav').clientHeight
            });
        })(target), isNarrow()? 444 : 0);
    }, true, (_, markerId) => {
        let target = document.querySelector(`.marker[marker-id=${markerId}]`);
        let name = getMarkerName(target);
        let li = Donggi.getElementFromText(`<li class="${target.classList.contains('fake')? 'w3-hide' : ''}" title="${name}" marker-id="${markerId}">${name.substr(0, 25)}</li>`);
        
        let main = document.querySelector('div#contents');
        let level = 0;
        while (target.parentElement != main) {
            target = target.parentElement;
            level += 1;
        }
        li.classList.add(`margin-left-${level}`);
        li.setAttribute('level', level);
        return li;
    }, false, () => {
        let levels = [0, 0, 0, 0, 0, 0, 0, 0];
        const headerTags = new Set(['H1', 'H2', 'H3', 'H4', 'H5', 'H6']);
        for (let li of document.getElementById('marker-list').querySelectorAll('li')) {
            let current = parseInt(li.getAttribute('level'));
            levels[current]++;
            levels.fill(0, current+1);
            let prefix = `${levels.filter(x => x > 0).join('.')}. `;
            li.innerText = `${prefix}${li.innerText}`;
            let target = document.querySelector(`.marker[marker-id=${li.getAttribute('marker-id')}]`);
            if (headerTags.has(target.tagName))
                target.innerText = `${prefix}${target.innerText}`;
        }
    });
}

function getMarkerName(marker) {
    let name;
    switch (marker.tagName) {
        case 'IMG':
            name = `이미지 : ${marker.alt}`;
            break;
        case 'TABLE':
            name = `표 : ${marker.caption.textContent}`;
            break;
        default:
            name = marker.textContent;
    }
    return name.replace(/\//gm, '\\');
}

function queryUpdated(e) {
    if (e.keyCode == 13) {
        Donggi.openLink(`https://github.com/Dong-gi/Dong-gi.github.io/search?q=${this.value}`, '_blank');
        event.stopPropagation();
    }
    let query = document.getElementById('query').value;
    let showAll = query.length < 2;
    let regex = new RegExp(query, 'gmi');
    if (showAll)
        document.getElementById('contents').childNodes.forEach((node, idx, nodeList) => !!node.style && (node.style.display = 'block'));
    else
        document.getElementById('contents').childNodes.forEach((node, idx, nodeList) => !!node.style && (node.style.display = regex.test(node.textContent)? 'block' : 'none'));
    return true;
}

function insertCodeDiv(id) {
    return () => {
        if (!document.getElementById(`code-div-${id}`)) {
            let button = document.getElementById(`code-button-${id}`);
            let path = button.title;
            let xhr = new XMLHttpRequest();
            xhr.addEventListener('load', ((button) => function(e) {
                let div = Donggi.getElementFromText(`<div id="code-div-${id}" class="w3-leftbar w3-border-green code-div" style="max-height:${window.innerHeight / 2}"></div>`);
                let lan = button.getAttribute('lan');
                if (this.status != 200)
                    this.responseText = 'Ajax Failed';

                posts.codes[id] = this.responseText;
                fillCodeDiv(div, lan, this.responseText, button.getAttribute('displayRange'));

                if (lan != 'nohighlight') {
                    let modal = Donggi.getElementFromText('<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-blue">모달로 보기</button>');
                    modal.onclick = showModal(id);
                    button.after(modal);
                    modal.after(div);
                } else
                    button.after(div);

                if (lan == 'javascript') {
                    let script = Donggi.getElementFromText('<button class="w3-btn w3-round w3-round-xxlarge w3-small w3-green">실행</button>');
                    script.onclick = () => eval(posts.codes[id]);
                    button.after(script);
                }
            })(button));
            if (!/dong-gi\.github\.io/i.test(location.host))
                xhr.open('GET', `${path.startsWith('/') ? '' : './'}${path.replace(/ /gm, '%20')}`, true);
            else
                xhr.open('GET', `${path.startsWith('/') ? '' : './'}${path.replace(/ /gm, '%20')}?${new Date().getTime()}`, true);
            xhr.send();
        } else {
            let div = document.getElementById(`code-div-${id}`);
            Donggi.toggleClass(div, ['w3-hide']);
            div.style.maxHeight = window.innerHeight / 2;
        }
    };
}

function fillCodeDiv(div, lan, text, displayRange) {
    if (lan != 'nohighlight') {
        let code = text.replace(/\t/gm, '    ');
        code = code.replace(/ /gm, '  ');

        let lines = null;
        if (lan === 'text')
            lines = code.split(/\n/gm);
        else
            lines = hljs.highlight(lan, code)['value'].split(/\n/gm);

        let ol = document.createElement('ol');
        displayRange = JSON.parse(displayRange || '[1, 100000000]') || [1, 100000000];
        displayRange = displayRange.reverse();

        while (displayRange.length > 0) {
            let displayStart = displayRange.pop() - 1;
            let displayEnd = displayRange.pop();
            for (let idx = displayStart; idx < displayEnd && idx < lines.length; ++idx) {
                if (lan === 'text') {
                    let li = document.createElement('li');
                    li.innerText = lines[idx].replace(/  /gm, '\u00A0');
                    ol.append(li);
                } else
                    ol.append(Donggi.getElementFromText(`<li>${lines[idx].replace(/  /gm, '&nbsp;')}</li>`));
            }
            if (displayRange.length > 0)
                ol.append(document.createElement('hr'));
        }
        div.append(ol);
    } else {
        div.append(Donggi.getNodesFromText(text, 'p'));
    }
}

function showModal(id) {
    return () => {
        closeSidebar();
        let modal = document.getElementById(`modal-${id}`);
        if (!!modal) {
            modal.style.display = 'block';
            return;
        }

        modal = Donggi.getElementFromText(getCodeModalHTML(id, document.getElementById(`code-button-${id}`).title.split('/').pop()));
        let header = modal.querySelector('header');
        let body = modal.querySelector('.code-modal-body');
        let footer = modal.querySelector('footer');
        
        document.body.append(modal);
        modal.style.display = 'block';
        body.innerHTML = document.getElementById(`code-div-${id}`).innerHTML;
        body.style.height = window.innerHeight - parseFloat(window.getComputedStyle(header).height);
        
        footer.querySelector('button.copy').onclick = () => {
            Donggi.copyTextToCilpboard(posts.codes[id], modal);
            Donggi.showSnackbar('복사 완료', modal);
            modal.focus();
        };
        footer.querySelector('button.download').onclick = () => downloadCode(document.getElementById(`code-button-${id}`).title.split('/').pop(), posts.codes[id]);
        footer.querySelector('button.print').onclick = () => Donggi.printElement(body);
        for (let node of modal.querySelectorAll('.w3-btn.close')) {
            node.onclick = () => {
                document.getElementById(`modal-${id}`).style.display = 'none';
                if (!isNarrow())
                    openSidebar();
            };
        }
    };
}

function downloadCode(fileName, text) {
    let a = document.createElement('a');
    let url = URL.createObjectURL(new Blob([text.trim()], {
        type: 'text/plain;charset=utf-8;'
    }));
    a.href = url;
    a.target = '_blank';
    a.download = fileName;
    document.body.append(a);
    a.click();
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
</div>`;
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
        this.target = document.querySelector(targetQuery);
        this.fileAction = (!!fileAction)? fileAction : null;
        this.openAll = !!openAll;
        this.fileHTMLmaker = (!!fileHTMLmaker)? fileHTMLmaker : null;
        this.sort = !!sort;
        this.target.innerHTML = '';
        this.fileMap = new Map();
        this.rootDir = null;
        this.callback = callback;
        
        if(!this.target)
            return;
        let xhr = new XMLHttpRequest();
        xhr.addEventListener('load', ((fileList) => function(e) {
            if (this.status != 200) {
                fileList.target.innerHTML = 'No Data';
                return;
            }
            let dir = null;
            for (let line of this.responseText.replace(/\r/gm, '').split('\n')) {
                if (line.endsWith(':')) {
                    dir = line.slice(0, -1);
                    if (!fileList.rootDir)
                        fileList.rootDir = dir;
                    fileList.fileMap.set(dir, []);
                } else if (line.length > 0)
                    fileList.fileMap.get(dir).push(line);
            }
            if (this.sort)
                for (let files of fileList.fileMap.values())
                    files.sort(Donggi.compareString);
            console.log(fileList.fileMap);
            fileList.updateFileList(fileList.rootDir);
            fileList.callback && fileList.callback(this);
        })(this));
        xhr.open('GET', lsResultPath, true);
        xhr.send();
    }
    
    updateFileList(dir) {
        let details = document.getElementById(`dir-${dir.hashCode()}`);
        if (dir == this.rootDir && !details) {
            this.target.append(DKFileList.getDirHTML(dir, '', true));
            this.updateFileList(dir);
            return;
        }

        let ul = details.querySelector('ul');
        if (ul.childElementCount > 1)
            return;

        for (let name of this.fileMap.get(dir)) {
            let path = `${dir}/${name}`;
            let isDir = this.fileMap.has(path);
            if (isDir) {
                while (this.fileMap.has(path)
                       && this.fileMap.get(path).length == 1
                       && this.fileMap.has(`${path}/${this.fileMap.get(path)[0]}`))
                    path = `${path}/${this.fileMap.get(path)[0]}`;
                if (this.fileMap.get(path).length < 1)
                    continue;
                ul.append(DKFileList.getDirHTML(path, dir, this.openAll));
                let dirAction = ((fileList, dir) => function (e) { fileList.updateFileList(dir); })(this, path);
                document.getElementById(`dir-${path.hashCode()}`).firstChild.onclick = dirAction;
                if (this.openAll)
                    this.updateFileList(path);
            } else {
                if (!!this.fileAction) {
                    let element = !!this.fileHTMLmaker? this.fileHTMLmaker(dir, name) : DKFileList.getFileHTMLwithoutA(dir, name);
                    ul.append(element);
                    let fileAction = ((fileList, dir, name) => function (e) { fileList.fileAction(dir, name); })(this, dir, name);
                    element.onclick = fileAction;
                } else
                    ul.append(!!this.fileHTMLmaker? this.fileHTMLmaker(dir, name) : DKFileList.getFileHTMLwithA(this.rootDir, dir, name));
            }
        }
    }
    
    static getDirHTML(dir, parentDir, open) {
        return Donggi.getElementFromText(`<details ${(!!open)? 'open' : ''} id="dir-${dir.hashCode()}" class="w3-small file-list" title="${dir}"><summary>${dir.replace(parentDir, '')}</summary><ul></ul></details>`);
    }
    
    static getFileHTMLwithA(rootDir, dir, name) {
        let path = `${dir.substr(rootDir.length)}/${name}`;
        return Donggi.getElementFromText(`<li><a href="${path}">${name}</a></li>`);
    }
    
    static getFileHTMLwithoutA(dir, name) {
        let path = `${dir}/${name}`;
        return Donggi.getElementFromText(`<li title="${path}">${name}</li>`);
    }
}
