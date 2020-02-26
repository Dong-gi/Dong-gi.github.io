String.prototype.hashCode = function () {
    let hash = 0, i, chr;
    if (this.length === 0) return hash;
    for (i = 0; i < this.length; i++) {
        chr   = this.charCodeAt(i);
        hash  = ((hash << 5) - hash) + chr;
        hash |= 0; // Convert to 32bit integer
    }
    return hash;
};
if (location.pathname.length > 2 && location.pathname.length != '/index.html') {
    let pathname = '/';
    let search = `?post=${location.pathname.replace(/.*gi\.github\.io([^?]+)[?]?/i, '$1').hashCode()}`;
    location.href = `${location.origin}${pathname}${search}`;
}