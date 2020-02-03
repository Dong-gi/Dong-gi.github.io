if (location.pathname.length > 2 && location.pathname.length != '/index.html') {
    let pathname = '/';
    let search = `?post=${location.pathname.replace(/.*gi\.github\.io([^?]+)[?]?/i, '$1').hashCode()}`;
    location.href = `${location.origin}${pathname}${search}`;
}