import matplotlib; matplotlib.use('Agg')
import matplotlib.pyplot as plt, matplotlib.dates as md, datetime as dt
B,O,G,T2='#257de0','#f47230','#666666','#191919'
plt.rcParams.update({'font.size':10,'axes.edgecolor':'#cccccc','axes.labelcolor':T2,'xtick.color':G,'ytick.color':G,'axes.spines.top':False,'axes.spines.right':False})
D=lambda s: dt.date(2026,11,int(s)) if isinstance(s,int) else s
def days(a,b):
    d=a; out=[]
    while d<=b: out.append(d); d+=dt.timedelta(1)
    return out
def remaining(dates, scope_ev, done_ev):
    sc=[];dn=[]
    for d in dates:
        sc.append(sum(p for t,p in scope_ev if t<=d)); dn.append(sum(p for t,p in done_ev if t<=d))
    return sc,dn
sprints={
 'Sprint 1 · Iteración 1 (19 oct – 1 nov)':(dt.date(2026,10,19),dt.date(2026,11,1),[(dt.date(2026,10,19),6)],[(dt.date(2026,10,28),3),(dt.date(2026,10,28),3)]),
 'Sprint 2 · Iteración 2 (2 – 15 nov)':(dt.date(2026,11,2),dt.date(2026,11,15),[(dt.date(2026,11,2),11)],[(dt.date(2026,11,7),3),(dt.date(2026,11,8),3),(dt.date(2026,11,8),5)]),
 'Sprint 3 · Iteración 3 (16 – 28 nov)':(D(16),D(28),[(D(16),22),(D(23),16)],[(D(16),11),(D(19),11),(D(23),3),(D(25),5),(D(26),3),(D(26),5)]),
}
fig,axs=plt.subplots(1,3,figsize=(15,4.6),dpi=150)
for ax,(title,(a,b,sc,dn)) in zip(axs,sprints.items()):
    ds=days(a,b); s,d=remaining(ds,sc,dn); rem=[x-y for x,y in zip(s,d)]
    total0=s[0]; n=len(ds)-1
    ideal=[total0*(1-i/n) for i in range(n+1)]
    ax.plot(ds,ideal,color=G,lw=1.5,ls='--',label='Ideal (alcance inicial)')
    ax.step(ds,rem,where='post',color=B,lw=2,label='Puntos pendientes')
    ax.set_title(title,color=T2,fontsize=11,loc='left'); ax.set_ylim(0,max(s)+3)
    ax.xaxis.set_major_formatter(md.DateFormatter('%d/%m')); ax.xaxis.set_major_locator(md.DayLocator(interval=3))
    ax.grid(axis='y',color='#eeeeee'); ax.set_ylabel('Puntos de historia pendientes')
    ax.annotate(f'{rem[-1]} SP al cierre',(ds[-1],rem[-1]),xytext=(-95,45),textcoords='offset points',color=T2,arrowprops=dict(arrowstyle='-',color=G))
    if 'Sprint 3' in title:
        ax.annotate('HU05, HU06 y HU08\nadelantadas en el Sprint 2',(D(16),11),xytext=(8,40),textcoords='offset points',fontsize=8.5,color=T2)
        ax.annotate('+16 SP de W8: HU11 (5),\nHU13 (3), HU14 (5), HU15 (3)',(D(23),13),xytext=(-40,62),textcoords='offset points',fontsize=8.5,color=T2,arrowprops=dict(arrowstyle='-',color=G))
axs[0].legend(frameon=False,loc='upper right')
fig.suptitle('Burndown por sprint · puntos de historia pendientes por día (fecha de integración en develop, hora de Colombia)',x=0.01,ha='left',fontsize=13,color=T2)
fig.tight_layout(); fig.savefig('burndown-sprints.png',facecolor='white')

# burnup sprint 3
a,b,sc,dn=sprints['Sprint 3 · Iteración 3 (16 – 28 nov)']; ds=days(a,b); s_,d=remaining(ds,sc,dn)
fig,ax=plt.subplots(figsize=(9,4.8),dpi=150)
ax.step(ds,s_,where='post',color=O,lw=2,label='Alcance comprometido')
ax.step(ds,d,where='post',color=B,lw=2,label='Puntos aceptados (integrados en develop)')
for t,lab,off in [(D(16),'HU05, HU06, HU08\n(adelantos del Sprint 2)',(4,8)),(D(19),'HU09, HU10, HU12',(4,-16)),(D(23),'HU15',(4,-14)),(D(25),'HU14',(4,-14)),(D(26),'HU13 y HU11',(4,-14))]:
    i=ds.index(t); ax.annotate(lab,(t,d[i]),xytext=off,textcoords='offset points',fontsize=8.5,color=T2)
ax.annotate('W8 (23 nov): +16 SP\nHU11 5 · HU13 3 · HU14 5 · HU15 3',(D(23),30),xytext=(-175,0),textcoords='offset points',fontsize=8.5,color=T2,arrowprops=dict(arrowstyle='-',color=G))
ax.set_ylim(0,42); ax.set_ylabel('Puntos de historia'); ax.grid(axis='y',color='#eeeeee')
ax.xaxis.set_major_formatter(md.DateFormatter('%d/%m')); ax.xaxis.set_major_locator(md.DayLocator(interval=2))
ax.set_title('Burnup del Sprint 3 · 38 de 38 puntos aceptados el 26 de noviembre',loc='left',color=T2)
ax.legend(frameon=False,loc='lower right'); fig.tight_layout(); fig.savefig('burnup-sprint-3.png',facecolor='white')
