javascript: (async function () {
    const w = document.querySelector('.about-wrapper');
    const title = w.querySelector('.about-header strong').textContent.trim();
    const directors = Array.from(w.querySelectorAll('.about-container span')).find(x => /(감독|크리에이터) *:/.test(x.textContent))?.parentNode?.textContent?.replace(/(감독|크리에이터) *:/, '')?.trim();
    const actors = Array.from(w.querySelectorAll('.about-container span')).find(x => /출연 *:/.test(x.textContent))?.parentNode?.textContent?.replace(/출연 *:/, '')?.trim();
    let jbv = location.search.slice(1).split('&').find(x => x.startsWith('jbv='))?.slice(4);
    if (jbv == null) {
        jbv = /title\/(\d+)/.exec(location.href)[1];
    }
    const txt = `${jbv}\t${title}\t${directors}\t${actors}`;
    while (true) {
        try {
            await navigator.clipboard.writeText(txt);
            console.log(txt);
            break;
        } catch {
            await new Promise(_ => setTimeout(_, 100));
            continue;
        }
    }
})().catch(console.error);