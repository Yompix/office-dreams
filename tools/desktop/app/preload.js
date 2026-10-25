// мостик между игрой и окном: игра видит window.nsDesktop (engine.js: DESK) — выйти из игры и переключить полный экран
const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('nsDesktop', {
  quit: () => ipcRenderer.send('ns-quit'),
  full: on => ipcRenderer.send('ns-full', on),
  platform: process.platform,
});
