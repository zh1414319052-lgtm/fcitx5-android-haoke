'use strict';
const get=id=>document.getElementById(id),data=window.HaokePrototypeData;
get('name').textContent=data.name+' · 版本 '+data.revision;
const player=new HaokeMotion.Player(get('screen'),data,info=>{get('status').textContent='当前状态：'+info.name+' · '+(info.playing?'播放中':'已停留');get('screen').dataset.state=info.state;get('progress').value=info.progress*1000;});
for(const [id,method]of [['reset','reset'],['back','back'],['replay','replay'],['pause','pause'],['play','resume']])get(id).onclick=()=>player[method]();
get('speed').onchange=e=>player.speed=Number(e.target.value);
get('reduced').onchange=e=>{player.reduced=e.target.checked;if(e.target.checked)player.finish();};
get('progress').oninput=e=>player.seek(Number(e.target.value)/1000);
window.addEventListener('pagehide',()=>player.dispose());
