# finds eyes and mouth on the drawn characters (public/art/stand_*, walk_*, npc_*): run from public/art, prints faces.json + marked previews into the given folder
# usage: cd public/art && python3 ../../tools/art/faces.py /tmp/out  (needs numpy, opencv-python-headless, PIL)
import sys, json, glob, numpy as np, cv2
from PIL import Image, ImageDraw
fs=sorted(glob.glob('stand_*.webp')+glob.glob('walk_*.webp')+glob.glob('npc_*.webp'))
res={}; tiles=[]
def find(a, w, h, thr, y0, y1, x0, x1):
    r,g,b,al=a[...,0],a[...,1],a[...,2],a[...,3]; lum=0.3*r+0.59*g+0.11*b
    dark=((al>200)&(lum<thr)).astype(np.uint8)
    box=np.zeros_like(dark); box[int(y0):int(y0+(y1-y0)*0.72)+1,int(x0):int(x1)+1]=1
    n,lab=cv2.connectedComponents(dark*box,connectivity=8)
    comps=[]
    for i in range(1,n):
        yy,xx=np.nonzero(lab==i)
        hh=yy.max()-yy.min()+1; ww=xx.max()-xx.min()+1
        if 12<=len(xx)<=400 and hh>=6 and ww/hh<2.6: comps.append((len(xx),xx.mean(),yy.mean(),xx.min(),xx.max(),yy.min(),yy.max()))
    best=None
    for i in range(len(comps)):
        for j in range(i+1,len(comps)):
            A,B=comps[i],comps[j]
            if abs(A[2]-B[2])>6 or abs(A[1]-B[1])<w*0.12 or abs(A[1]-B[1])>w*0.45: continue
            sc=A[0]+B[0]-abs(A[0]-B[0])*1.5
            if not best or sc>best[0]: best=(sc,A,B)
    return best
for f in fs:
    im=Image.open(f).convert('RGBA'); a=np.array(im).astype(int); h,w=a.shape[:2]
    r,g,b,al=a[...,0],a[...,1],a[...,2],a[...,3]; lum=0.3*r+0.59*g+0.11*b
    skin=(al>200)&(r>g)&(g>b)&(r-b>25)&(lum>90)
    region=np.zeros_like(skin); region[int(h*0.08):int(h*0.38),:]=True
    ys,xs=np.nonzero(skin&region)
    x0,x1=np.percentile(xs,5),np.percentile(xs,95); y0,y1=np.percentile(ys,3),np.percentile(ys,95)
    best=None
    for thr in (70,85,100):
        best=find(a,w,h,thr,y0,y1,x0,x1)
        if best: break
    if not best: print('noeyes',f); continue
    L,R=sorted([best[1],best[2]],key=lambda c:c[1])
    ey=(L[5]+R[5]+L[6]+R[6])/4; ew=max(L[4]-L[3],R[4]-R[3])+2; eh=max(L[6]-L[5],R[6]-R[5])+2
    dist=R[1]-L[1]; my=ey+eh/2+dist*0.62; mx=(L[1]+R[1])/2
    k=f[:-5]; res[k]=dict(l=[round(L[1]),round(ey)],r=[round(R[1]),round(ey)],ew=int(ew),eh=int(eh),m=[round(mx),round(my)],mw=round(dist*0.45))
    d=ImageDraw.Draw(im)
    for c in (res[k]['l'],res[k]['r']): d.rectangle((c[0]-ew/2,c[1]-eh/2,c[0]+ew/2,c[1]+eh/2),outline=(0,255,0,255))
    d.rectangle((mx-dist*0.22,my-2,mx+dist*0.22,my+2),outline=(255,0,0,255))
    c=im.crop((0,0,w,int(h*0.42))); bg=Image.new('RGBA',c.size,(60,60,80,255)); bg.alpha_composite(c); tiles.append(bg.resize((c.width*2,c.height*2),Image.NEAREST))
json.dump(res,open(sys.argv[1]+'/faces.json','w'))
half=(len(tiles)+1)//2
for n,ts in enumerate((tiles[:half],tiles[half:])):
    W=sum(t.width for t in ts); out=Image.new('RGB',(W,max(t.height for t in ts))); x=0
    for t in ts: out.paste(t,(x,0)); x+=t.width
    out.save(sys.argv[1]+'/fm%d.png'%(n+1))
print(len(res))
