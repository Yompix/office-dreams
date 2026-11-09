/* Офлайн-сборка (itch.io «скачать»): браузер не даёт читать файлы с диска (file://) через XHR,
   поэтому звуки, модели и тайминги лежат в pack-*.js как base64, а этот шим подменяет ответ XHR. */
(function () {
  const P = window.__PACK = window.__PACK || {};
  const key = u => decodeURIComponent(String(u).split('?')[0].replace(/^\.\//, ''));
  const bin = s => { const b = atob(s), a = new Uint8Array(b.length); for (let i = 0; i < b.length; i++) a[i] = b.charCodeAt(i); return a.buffer; };
  const open = XMLHttpRequest.prototype.open, send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (m, u) { this.__k = key(u); return open.apply(this, arguments); };
  XMLHttpRequest.prototype.send = function () {
    const k = this.__k;
    if (!(k in P)) return send.apply(this, arguments);
    setTimeout(() => {
      let r = bin(P[k]);
      if (this.responseType === 'json') r = JSON.parse(new TextDecoder().decode(r));
      else if (this.responseType === 'text' || !this.responseType) r = new TextDecoder().decode(r);
      Object.defineProperty(this, 'status', { value: 200 }); Object.defineProperty(this, 'readyState', { value: 4 });
      Object.defineProperty(this, 'response', { value: r });
      if (typeof r === 'string') Object.defineProperty(this, 'responseText', { value: r });
      this.dispatchEvent(new Event('load')); this.dispatchEvent(new Event('loadend'));
    }, 0);
  };
})();
