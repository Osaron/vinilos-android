import matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt, re, statistics as st, sys
R=sys.argv[1]; OUT=sys.argv[2]
B,O,A,G,T2='#257de0','#f47230','#00b894','#666666','#191919'
plt.rcParams.update({'font.size':10,'axes.edgecolor':'#cccccc','axes.labelcolor':T2,'xtick.color':G,'ytick.color':G,'axes.spines.top':False,'axes.spines.right':False})
# 1 arranque
t=[int(x) for x in re.findall(r'TotalTime: (\d+)',open(f'{R}/arranque-frio.txt').read())]
med=st.median(t)
fig,ax=plt.subplots(figsize=(8,4),dpi=150)
ax.bar(range(1,len(t)+1),t,color=B,width=0.6)
ax.axhline(med,color=G,ls='--',lw=1.5); ax.text(len(t)+0.45,med,f'mediana {med:.0f} ms',va='center',color=T2,fontsize=9)
ax.set_xticks(range(1,len(t)+1)); ax.set_xlabel('Arranque en frío #'); ax.set_ylabel('TotalTime (ms)'); ax.grid(axis='y',color='#eeeeee')
ax.set_title(f'PERF-01 · Arranque en frío hasta el catálogo · Pixel 6 API 34 (emulador) · máx. {max(t)} ms',loc='left',color=T2,fontsize=10.5)
ax.set_xlim(0.4,len(t)+2.2); fig.tight_layout(); fig.savefig(f'{OUT}/perf-arranque-frio.png',facecolor='white')
# 2 tiempos de respuesta
rows=[('HU09 · abrir detalle con comentarios',43),('HU09 · publicar → comentario visible',65),('HU10 · estado de favoritos listo',37),('HU10 · agregar favorito → confirmación',32),
('HU13 · abrir catálogo de premios',80.5),('HU13 · crear premio → visible',79.4),('HU12 · abrir selector de músicos (API 21)',300),('HU12 · abrir selector de músicos (API 34)',349)]
fig,ax=plt.subplots(figsize=(8.5,4.3),dpi=150)
y=range(len(rows))[::-1]
ax.barh(list(y),[r[1] for r in rows],color=B,height=0.55)
for yy,(n,v) in zip(y,rows): ax.text(v+5,yy,f'{v:g} ms',va='center',fontsize=9,color=T2)
ax.set_yticks(list(y)); ax.set_yticklabels([r[0] for r in rows]); ax.set_xlabel('Mediana (ms) · 10 a 20 repeticiones'); ax.grid(axis='x',color='#eeeeee')
ax.axvline(100,color=G,ls=':',lw=1); ax.text(102,len(rows)-0.4,'100 ms',color=G,fontsize=8)
ax.set_xlim(0,400); ax.set_title('Tiempo de respuesta por historia (mediana)',loc='left',color=T2)
fig.tight_layout(); fig.savefig(f'{OUT}/perf-tiempos-respuesta.png',facecolor='white')
# 3 memoria
mem=[('HU04 · 100 ciclos\ndetalle de artista',83.9,104.6,85.2),('HU09 · 20 ciclos\ncomentarios',73.6,145.9,132.1),('HU10 · 20 ciclos\nfavoritos',86.1,91.6,90.7),('HU12 · 10 ciclos\nAPI 34',81.3,None,83.3),('HU13 · 20 ciclos\npremios',94.8,None,88.2),('Monkey · 2 × 500\neventos',None,81.9,79.1)]
import numpy as np
x=np.arange(len(mem)); w=0.26
for i,(lab,c) in enumerate([('Inicio',G),('Después de los ciclos',O),('Final / tras GC o reposo',B)]):
    vals=[m[1+i] for m in mem]
    xs=[xx+(i-1)*w for xx,v in zip(x,vals) if v is not None]; vs=[v for v in vals if v is not None]
    bars=ax2=None
    plt.figure(1)
fig,ax=plt.subplots(figsize=(10,4.6),dpi=150)
for i,(lab,c) in enumerate([('Inicio',G),('Después de los ciclos',O),('Final (tras GC o en reposo)',B)]):
    vals=[m[1+i] for m in mem]
    xs=[xx+(i-1)*w for xx,v in zip(x,vals) if v is not None]; vs=[v for v in vals if v is not None]
    ax.bar(xs,vs,width=w-0.03,color=c,label=lab)
    for xx,v in zip(xs,vs): ax.text(xx,v+1.5,f'{v:.0f}',ha='center',fontsize=8,color=T2)
ax.set_xticks(x); ax.set_xticklabels([m[0] for m in mem],fontsize=8.5); ax.set_ylabel('PSS total (MB)'); ax.grid(axis='y',color='#eeeeee'); ax.set_ylim(0,165)
ax.legend(frameon=False,ncol=3,loc='upper right'); ax.set_title('Memoria (PSS) antes y después de recorridos repetidos',loc='left',color=T2)
fig.tight_layout(); fig.savefig(f'{OUT}/perf-memoria.png',facecolor='white')
# 4 fotogramas
fig,ax=plt.subplots(figsize=(8,4),dpi=150)
labs=['p50','p90','p95','p99']
for i,(s,c) in enumerate([('20261122',B),('20261123',O)]):
    txt=open(f'{R}/gfxinfo-{s}.txt').read()
    v=[int(re.search(rf'{p}th percentile: (\d+)ms',txt).group(1)) for p in (50,90,95,99)]
    xs=np.arange(4)+(i-0.5)*0.36
    ax.bar(xs,v,width=0.34,color=c,label=f'Monkey semilla {s}')
    for xx,vv in zip(xs,v): ax.text(xx,vv+15,str(vv),ha='center',fontsize=8,color=T2)
ax.set_xticks(range(4)); ax.set_xticklabels(['Percentil 50','Percentil 90','Percentil 95','Percentil 99']); ax.set_ylabel('Tiempo por fotograma (ms)')
ax.axhline(16.7,color=G,ls=':',lw=1); ax.text(3.45,30,'16,7 ms (60 fps)',color=G,fontsize=8,ha='right')
ax.grid(axis='y',color='#eeeeee'); ax.legend(frameon=False,loc='upper left'); ax.set_title('Fluidez durante Monkey · gfxinfo en emulador con GPU por software',loc='left',color=T2,fontsize=10.5)
fig.tight_layout(); fig.savefig(f'{OUT}/perf-fotogramas-monkey.png',facecolor='white')
print(t,med)
