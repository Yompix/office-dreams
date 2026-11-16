import subprocess,sys,time,threading,queue
p=subprocess.Popen(["java","-Xmx2G","-jar",sys.argv[1],"nogui"],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,bufsize=1)
q=queue.Queue()
def rd():
    for l in p.stdout: q.put(l)
threading.Thread(target=rd,daemon=True).start()
def wait(pat,t=600):
    end=time.time()+t
    while time.time()<end:
        try: l=q.get(timeout=1)
        except queue.Empty: continue
        print(l,end='',flush=True)
        if pat in l: return l
def cmd(c,pat=None,t=120):
    p.stdin.write(c+'\n'); p.stdin.flush()
    if pat: return wait(pat,t)
wait('Done (')
for c in sys.argv[2:]:
    cmd(c, 'INFO]', 300)
    time.sleep(1)
cmd('stop'); p.wait(120)
