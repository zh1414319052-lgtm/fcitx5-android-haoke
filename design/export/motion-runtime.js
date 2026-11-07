/* Offline prototype adapter: Anime.js 4.5.0 + XState 5.33.3 (MIT).
 * Declarative states only. No user code, network, business mutations or HTML injection.
 */
(function(root){
'use strict';
const NS='http://www.w3.org/2000/svg', defaults={translateX:0,translateY:0,scale:1,opacity:1};
const copy=x=>JSON.parse(JSON.stringify(x)), clamp=x=>Math.max(0,Math.min(1,x));
function el(tag,attrs={}){const n=document.createElementNS(NS,tag);for(const[k,v]of Object.entries(attrs))n.setAttribute(k,v);return n;}
function resolve(value,tokens){let n=0;while(typeof value==='string'&&value.startsWith('$')){value=tokens[value.slice(1)];if(++n>20)throw Error('Invalid token chain');}return value;}
function resolveSpec(spec,tokens={}){const m=copy(spec);for(const s of m.states)for(const v of Object.values(s.overrides||{}))for(const k of Object.keys(v))v[k]=resolve(v[k],tokens);for(const t of m.transitions){t.duration=resolve(t.duration??280,tokens);for(const name of ['gesture','spring'])if(t[name])for(const k of Object.keys(t[name]))t[name][k]=resolve(t[name][k],tokens);}return m;}
function makeActor(spec){
 const keys=new Map(spec.states.map((s,i)=>[s.id,'s'+i])), reverse=new Map([...keys].map(([a,b])=>[b,a]));
 const states={};for(const s of spec.states)states[keys.get(s.id)]={on:{}};
 for(const t of spec.transitions)states[keys.get(t.from)].on[t.id]={target:keys.get(t.to)};
 const actor=root.XState.createActor(root.XState.createMachine({id:'prototype',initial:keys.get(spec.initial),states}));actor.start();
 return {actor,get value(){return reverse.get(actor.getSnapshot().value);},send:t=>actor.send({type:t.id}),stop:()=>actor.stop()};
}
function renderBoard(canvas,board){
 canvas.replaceChildren();canvas.setAttribute('viewBox',`0 0 ${board.width} ${board.height}`);canvas.style.background=board.background;
 const lookup=new Map(),defs=el('defs');canvas.append(defs);let clip=0;
 function draw(nodes,parent){for(const n of nodes){if(n.hidden)continue;
  const wrap=el('g',{'data-motion-node':n.id});parent.append(wrap);lookup.set(n.id,{wrap,node:n});
  const a={x:n.x,y:n.y,width:n.width,height:n.height,fill:n.fill||'none',stroke:n.stroke||'none','stroke-width':n.strokeWidth||0,opacity:n.opacity};let shape;
  if(n.type==='rect'||(['group','instance'].includes(n.type)&&n.fill&&n.fill!=='none'))shape=el('rect',{...a,rx:n.radius||0});
  if(n.type==='ellipse')shape=el('ellipse',{cx:n.x+n.width/2,cy:n.y+n.height/2,rx:n.width/2,ry:n.height/2,fill:n.fill||'none',stroke:n.stroke||'none','stroke-width':n.strokeWidth||0,opacity:n.opacity});
  if(n.type==='path'){shape=el('svg',{x:n.x,y:n.y,width:n.width,height:n.height,viewBox:`0 0 ${n.localWidth} ${n.localHeight}`,overflow:'visible'});shape.append(el('path',{d:n.d,fill:n.fill||'none',stroke:n.stroke||'none','stroke-width':n.strokeWidth||0,opacity:n.opacity}));}
  if(n.type==='image'){shape=el('image',{...a,href:n.src,preserveAspectRatio:'none'});if(n.radius){const id='mc'+(++clip),cp=el('clipPath',{id});cp.append(el('rect',{x:n.x,y:n.y,width:n.width,height:n.height,rx:n.radius}));defs.append(cp);shape.setAttribute('clip-path',`url(#${id})`);}}
  if(n.type==='text'){
   shape=el('foreignObject',{x:n.x,y:n.y,width:n.width,height:n.height,opacity:n.opacity,overflow:'visible'});
   const div=document.createElementNS('http://www.w3.org/1999/xhtml','div');
   Object.assign(div.style,{fontFamily:'Noto',fontSize:(n.fontSize||16)*(n.scaleY||1)+'px',fontWeight:String(n.fontWeight||400),lineHeight:String(n.lineHeight||1.4),color:n.fill||'#000',width:'100%',height:'100%',whiteSpace:'pre-wrap',overflowWrap:'anywhere',textAlign:n.align||'left',display:'flex',flexDirection:'column',justifyContent:n.verticalAlign==='center'?'center':n.verticalAlign==='bottom'?'flex-end':'flex-start',pointerEvents:'none'});
   const span=document.createElement('span');span.textContent=n.text;div.append(span);shape.append(div);
  }
  if(shape)wrap.append(shape);if(n.children)draw(n.children,wrap);
 }}draw(board.children,canvas);return lookup;
}
class Player{
 constructor(canvas,data,onChange=()=>{}){
  this.canvas=canvas;this.data=data;this.spec=resolveSpec(data.motion,data.tokens);this.onChange=onChange;this.speed=1;this.reduced=false;this.animation=null;this.visual={};this.pointer=null;this.last=null;this.disposed=false;
  this.media=matchMedia('(prefers-reduced-motion: reduce)');this.abort=new AbortController();const signal=this.abort.signal;
  canvas.addEventListener('click',e=>this.click(e),{signal});canvas.addEventListener('keydown',e=>{if(e.key==='Escape'){e.preventDefault();this.back();}else if(e.key==='Enter'||e.key===' '){const t=this.match(e,'click');if(t){e.preventDefault();this.run(t);}}},{signal});
  canvas.addEventListener('pointerdown',e=>this.down(e),{signal});canvas.addEventListener('pointermove',e=>this.move(e),{signal});window.addEventListener('pointerup',e=>this.up(e,false),{signal});window.addEventListener('pointercancel',e=>this.up(e,true),{signal});canvas.addEventListener('pointerleave',()=>{if(this.pointer&&!this.pointer.moved)this.cancelPointer();},{signal});
  document.addEventListener('visibilitychange',()=>{if(document.hidden){this.cancelPointer();this.wasPlaying=!!this.animation&&!this.animation.paused;this.animation?.pause();}else if(this.wasPlaying){this.animation?.play();this.wasPlaying=false;}},{signal});
  this.media.addEventListener('change',()=>{if(this.reduceMotion())this.finish();},{signal});this.reset();
 }
 get current(){return this.machine.value;}
 state(id){return this.spec.states.find(s=>s.id===id);}
 reduceMotion(){return this.reduced||this.media.matches;}
 notify(extra={}){if(!this.disposed)this.onChange({state:this.current,name:this.state(this.current).name||this.current,progress:this.animation?clamp(this.animation.currentTime/this.animation.duration):1,playing:!!this.animation&&!this.animation.paused,...extra});}
 reset(){this.cancelPointer();this.animation?.cancel();this.machine?.stop();this.machine=makeActor(this.spec);this.last=null;this.show(this.state(this.current));this.visual=this.values(this.state(this.current));this.paint(this.visual);this.notify();}
 show(s){this.board=this.data.boards.find(b=>b.id===s.board);this.lookup=renderBoard(this.canvas,this.board);this.canvas.style.width=`min(360px,${60*this.board.width/this.board.height}vh)`;this.canvas.style.height='auto';this.canvas.style.aspectRatio=String(this.board.width/this.board.height);this.canvas.style.maxHeight='none';this.canvas.style.transform='';this.canvas.style.opacity='1';this.decorate();}
 values(s){const values={};const board=this.data.boards.find(b=>b.id===s.board);const visit=nodes=>{for(const n of nodes){values[n.id]={...defaults,...s.overrides?.[n.id]};visit(n.children||[]);}};visit(board.children);return values;}
 paint(values){this.visual=copy(values);for(const[id,v]of Object.entries(values)){const entry=this.lookup.get(id);if(!entry)continue;const {wrap,node:n}=entry,cx=n.x+n.width/2,cy=n.y+n.height/2;wrap.setAttribute('transform',`translate(${v.translateX} ${v.translateY}) translate(${cx} ${cy}) scale(${v.scale}) translate(${-cx} ${-cy})`);wrap.setAttribute('opacity',clamp(v.opacity));wrap.style.visibility=v.opacity<.001?'hidden':'';}}
 decorate(){for(const {wrap}of this.lookup.values()){wrap.removeAttribute('tabindex');wrap.removeAttribute('role');wrap.style.cursor='';}for(const t of this.spec.transitions.filter(t=>t.from===this.current&&t.node)){const entry=this.lookup.get(t.node);if(entry){entry.wrap.setAttribute('role','button');entry.wrap.setAttribute('tabindex','0');entry.wrap.setAttribute('aria-label',t.name||t.id);entry.wrap.style.cursor='pointer';}}}
 match(e,trigger){for(let n=e.target;n&&n!==this.canvas;n=n.parentElement){const id=n.getAttribute?.('data-motion-node');const t=this.spec.transitions.find(t=>t.from===this.current&&t.trigger===trigger&&t.node===id);if(t)return t;}return null;}
 click(e){if(this.suppressClick){this.suppressClick=false;return;}const t=this.match(e,'click');if(t)this.run(t);}
 back(){this.cancelPointer();const t=this.spec.transitions.find(t=>t.from===this.current&&t.trigger==='back');if(t)this.run(t);}
 run(t){if(this.disposed||t.from!==this.current)return;this.cancelPointer();this.animation?.cancel();const before=copy(this.visual),source=this.state(this.current);this.machine.send(t);const target=this.state(this.current),cross=target.board!==source.board;
  if(cross)this.show(target);else this.decorate();const end=this.values(target),start=cross?copy(end):before;
  this.last={t:copy(t),start:copy(start),end:copy(end),cross};this.animate(start,end,t,cross);}
 animate(start,end,t,cross=false,settled=null){this.animation?.cancel();this.animation=null;if(!cross){this.canvas.style.opacity="1";this.canvas.style.transform="";}const meter={p:0},spring=t.easing==='spring'?root.anime.spring({mass:1,stiffness:180,damping:24,...t.spring}):null;
  const duration=(this.reduceMotion()?0:Math.min(5000,spring?.duration??t.duration??280))/this.speed;
  const paint=p=>{const values={};for(const[id,b]of Object.entries(end)){const a=start[id]||b;values[id]={};for(const k of Object.keys(defaults))values[id][k]=a[k]+(b[k]-a[k])*p;}this.paint(values);
   if(cross){const effect=t.pageEffect||'fade',shift=effect==='slideLeft'?24:effect==='slideRight'?-24:0;this.canvas.style.opacity=effect==='none'?'1':String(clamp(p));this.canvas.style.transform=`translateX(${shift*(1-p)}px)`;}};
  if(!duration){paint(1);settled?.();this.notify({progress:1,playing:false});return;}
  this.animation=root.anime.animate(meter,{p:[0,1],duration,ease:spring?.ease||t.easing||'outCubic',autoplay:false,onUpdate:()=>{paint(meter.p);this.notify();},onComplete:()=>{paint(1);settled?.();this.notify({progress:1,playing:false});}});paint(0);this.animation.play();this.notify();
 }
 replay(){this.cancelPointer();if(this.last){const l=this.last;this.animate(l.start,l.end,l.t,l.cross);}}
 pause(){this.animation?.pause();this.notify();}
 resume(){this.animation?.play();this.notify();}
 seek(fraction){this.cancelPointer();if(this.animation){this.animation.pause();this.animation.seek(clamp(fraction)*this.animation.duration);this.notify({playing:false});}}
 finish(){if(this.animation){this.animation.pause();this.animation.seek(this.animation.duration);this.notify({playing:false,progress:1});}}
 down(e){if(e.button!==0||this.pointer)return;const t=this.match(e,'drag'),long=this.match(e,'longpress');if(!t&&!long)return;
  this.pointer={id:e.pointerId,x:e.clientX,y:e.clientY,t,long,start:copy(this.visual),moved:false,progress:0};
  if(long)this.pointer.timer=setTimeout(()=>{if(this.pointer&&!this.pointer.moved){this.suppressClick=true;this.run(long);}},500);
 }
 move(e){const p=this.pointer;if(!p||p.id!==e.pointerId)return;const dx=e.clientX-p.x,dy=e.clientY-p.y;if(Math.hypot(dx,dy)>5){if(!p.moved&&p.t)this.canvas.setPointerCapture(p.id);p.moved=true;clearTimeout(p.timer);}if(!p.t||!p.moved)return;e.preventDefault();this.animation?.cancel();
  const g=p.t.gesture||{},scale=this.board.width/this.canvas.getBoundingClientRect().width;const fraction=clamp((g.axis==='x'?dx:dy)*scale/(g.distance??200));p.progress=fraction;
  const end=this.values(this.state(p.t.to)),values={};for(const[id,b]of Object.entries(end)){const a=p.start[id]||b;values[id]={};for(const k of Object.keys(defaults))values[id][k]=a[k]+(b[k]-a[k])*fraction;}this.paint(values);this.notify({progress:fraction,playing:false,gesture:true});
 }
 up(e,cancelled){const p=this.pointer;if(!p||p.id!==e.pointerId)return;this.pointer=null;clearTimeout(p.timer);if(this.canvas.hasPointerCapture(p.id))this.canvas.releasePointerCapture(p.id);if(!p.t||!p.moved)return;this.suppressClick=true;
  const accept=!cancelled&&p.progress>=(p.t.gesture?.threshold??.4),start=copy(this.visual);if(accept)this.machine.send(p.t);this.decorate();const end=this.values(this.state(this.current));
  this.last={t:copy(p.t),start:copy(start),end:copy(end),cross:false};this.animate(start,end,{...p.t,duration:Math.max(80,(p.t.duration??280)*(accept?1-p.progress:p.progress))});
 }
 cancelPointer(){const p=this.pointer;if(!p)return;clearTimeout(p.timer);this.pointer=null;if(this.canvas.hasPointerCapture(p.id))this.canvas.releasePointerCapture(p.id);if(p.moved){this.paint(this.values(this.state(this.current)));this.suppressClick=true;}}
 dispose(){this.disposed=true;this.cancelPointer();this.animation?.cancel();this.machine?.stop();this.abort.abort();}
}
root.HaokeMotion={Player,resolveSpec,makeActor,defaults};
})(typeof window==='undefined'?globalThis:window);
