const fs=require('fs'), path=require('path'), zlib=require('zlib');
const I=require('../src/icons.js');
// reuse png writer from render test
function crc32(buf){let c,crc=0xffffffff;for(let n=0;n<buf.length;n++){c=(crc^buf[n])&0xff;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;crc=(crc>>>8)^c;}return(crc^0xffffffff)>>>0;}
function chunk(t,d){const l=Buffer.alloc(4);l.writeUInt32BE(d.length);const td=Buffer.concat([Buffer.from(t),d]);const c=Buffer.alloc(4);c.writeUInt32BE(crc32(td));return Buffer.concat([l,td,c]);}
function png(w,h,rgba){const raw=Buffer.alloc((w*4+1)*h);for(let y=0;y<h;y++){raw[y*(w*4+1)]=0;rgba.copy(raw,y*(w*4+1)+1,y*w*4,(y+1)*w*4);}const ih=Buffer.alloc(13);ih.writeUInt32BE(w,0);ih.writeUInt32BE(h,4);ih[8]=8;ih[9]=6;return Buffer.concat([Buffer.from([137,80,78,71,13,10,26,10]),chunk('IHDR',ih),chunk('IDAT',zlib.deflateSync(raw)),chunk('IEND',Buffer.alloc(0))]);}
const names=Object.keys(I.ICONS); const S=6, cell=11*S+10; const cols=9; const rows=Math.ceil(names.length/cols);
const W=cols*cell, H=rows*cell; const buf=Buffer.alloc(W*H*4);
for(let i=0;i<W*H;i++){buf[i*4]=14;buf[i*4+1]=20;buf[i*4+2]=70;buf[i*4+3]=255;}
const hex=(h)=>[parseInt(h.slice(1,3),16),parseInt(h.slice(3,5),16),parseInt(h.slice(5,7),16)];
names.forEach((n,idx)=>{const ox=(idx%cols)*cell+5, oy=Math.floor(idx/cols)*cell+5;I.ICONS[n].forEach((row,y)=>{for(let x=0;x<row.length;x++){const ch=row[x];if(ch==='.')continue;const c=ch==='#'?[34,230,255]:hex(I.ICON_PAL[ch]);for(let dy=0;dy<S;dy++)for(let dx=0;dx<S;dx++){const p=((oy+y*S+dy)*W+(ox+x*S+dx))*4;buf[p]=c[0];buf[p+1]=c[1];buf[p+2]=c[2];}}});});
fs.writeFileSync(path.join(__dirname,'out','icons.png'),png(W,H,buf));
console.log(names.join(' '));
