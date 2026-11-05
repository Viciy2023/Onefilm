let req = (url, options) => http(url, Object.assign({
    async: false
}, options));

function http(url, options = {}) {
    if (options?.async === false) return _http(url, options)
    return new Promise(resolve => _http(url, Object.assign({
        complete: res => resolve(res)
    }, options))).catch(err => {
        console.error(err.name, err.message, err.stack)
        return {
            ok: false,
            status: 500,
            url
        }
    })
}

function defineGlobalAlias(name) {
    const descriptor = Object.getOwnPropertyDescriptor(globalThis, name);
    if (descriptor && !descriptor.configurable) return;
    Object.defineProperty(globalThis, name, {
        enumerable: true,
        configurable: true,
        get() {
            return globalThis;
        },
        set() {}
    });
}

['global', 'window', 'self'].forEach(defineGlobalAlias);

// drpy 兼容：部分源的 init / 预处理 会直接调用全局 pd（TVBox 会注入该函数）。
// drpy 语义：取第一个匹配元素，再把结果按 base_url 拼成绝对地址。
// 定义在 http.js 里，函数体内的 pdfh / joinUrl 在调用时解析，因此不受后面的宿主注入顺序影响。
if (typeof globalThis.pd !== 'function') {
    globalThis.pd = function (html, parse, base_url) {
        try {
            if (!html || parse === undefined || parse === null || !String(parse).trim()) return '';
            const value = pdfh(html, String(parse).trim());
            if (!value) return '';
            return base_url ? joinUrl(base_url, value) : value;
        } catch (e) {
            return '';
        }
    };
}
