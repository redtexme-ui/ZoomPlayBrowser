(function(){
'use strict';
if(window.__zoomPlay && window.__zoomPlay.version===1) return;
const state={video:null,savedStyle:null,savedBodyOverflow:null,savedRootOverflow:null,active:false,fit:'contain',zoom:1,lastError:''};
function allVideos(){return Array.from(document.querySelectorAll('video'))}
function orderedVideos(){
 return allVideos().map(v=>{
   const r=v.getBoundingClientRect(),s=getComputedStyle(v);
   const visible=r.width>50&&r.height>50&&s.display!=='none'&&s.visibility!=='hidden';
   return {v,score:(visible?1000000:0)+Math.min(r.width*r.height,900000)+(!v.paused?500000:0)};
 }).sort((a,b)=>b.score-a.score).map(x=>x.v);
}
function current(){return state.video&&state.video.isConnected?state.video:orderedVideos()[0]||null}
function exit(){
 if(state.active&&state.video) {
   if(state.savedStyle===null) state.video.removeAttribute('style');
   else state.video.setAttribute('style',state.savedStyle);
 }
 if(document.body&&state.savedBodyOverflow!==null) document.body.style.overflow=state.savedBodyOverflow;
 if(state.savedRootOverflow!==null) document.documentElement.style.overflow=state.savedRootOverflow;
 state.video=null;state.active=false;state.savedStyle=null;
 return {ok:true};
}
function show(v){
 if(state.active&&state.video!==v) exit();
 if(!state.active){
  state.savedStyle=v.getAttribute('style');
  state.savedBodyOverflow=document.body?document.body.style.overflow:null;
  state.savedRootOverflow=document.documentElement.style.overflow;
 }
 state.video=v;state.active=true;state.lastError='';
 v.setAttribute('playsinline','');
 if(document.body)document.body.style.overflow='hidden';
 document.documentElement.style.overflow='hidden';
 v.style.cssText=[
 'position:fixed!important','top:0!important','left:0!important',
 'width:100vw!important','height:100vh!important',
 'min-width:0!important','min-height:0!important',
 'max-width:none!important','max-height:none!important',
 'margin:0!important','padding:0!important',
 'z-index:2147483640!important','background:#000!important',
 'border:0!important','border-radius:0!important',
 'object-position:center center!important',
 'object-fit:'+state.fit+'!important',
 'transform:scale('+state.zoom+')!important',
 'transform-origin:50% 50%!important'
 ].join(';');
}
function focus(index){
 const vs=orderedVideos();
 if(!vs.length)return {ok:false,reason:'No accessible HTML5 video found. Start the video on the site or open its player directly.'};
 show(vs[Math.min(Math.max(index||0,0),vs.length-1)]);
 return {ok:true,count:vs.length};
}
function play(){
 const v=current();if(!v)return {ok:false,reason:'No accessible video on this page'};
 state.lastError='';v.setAttribute('playsinline','');v.autoplay=true;
 try{const p=v.play();if(p&&typeof p.catch==='function')p.catch(e=>{state.lastError='Playback blocked: '+(e&&e.message||String(e))});return {ok:true}}
 catch(e){state.lastError=e.message||String(e);return {ok:false,reason:state.lastError}}
}
function pause(){const v=current();if(!v)return {ok:false,reason:'No video found'};v.pause();return {ok:true}}
function toggle(){const v=current();return v&&!v.paused?pause():play()}
function seek(d){const v=current();if(!v||!Number.isFinite(v.duration))return {ok:false,reason:'Not seekable yet'};v.currentTime=Math.min(Math.max(0,v.currentTime+d),v.duration);return {ok:true}}
function goTo(t){const v=current();if(!v||!Number.isFinite(v.duration))return {ok:false,reason:'Not seekable yet'};v.currentTime=Math.min(Math.max(0,t),v.duration);return {ok:true}}
function setZoom(z){state.zoom=Math.min(3,Math.max(1,Number(z)||1));if(state.active&&state.video)state.video.style.setProperty('transform','scale('+state.zoom+')','important');return {ok:true,zoom:state.zoom}}
function setFit(f){state.fit=f==='cover'?'cover':'contain';if(state.active&&state.video)state.video.style.setProperty('object-fit',state.fit,'important');return {ok:true,fit:state.fit}}
function setSpeed(x){const v=current();if(!v)return {ok:false,reason:'No video found'};v.playbackRate=Math.min(2,Math.max(.5,Number(x)||1));return {ok:true,speed:v.playbackRate}}
function next(){const vs=orderedVideos();if(!vs.length)return {ok:false,reason:'No video found'};return focus(state.video?(vs.indexOf(state.video)+1)%vs.length:0)}
function status(){const v=current();return {ok:!!v,paused:v?v.paused:true,count:allVideos().length,current:v&&Number.isFinite(v.currentTime)?v.currentTime:0,duration:v&&Number.isFinite(v.duration)?v.duration:0,error:state.lastError}}
function frameInfo(){
 const frames=Array.from(document.querySelectorAll('iframe[src]')).map(f=>{const r=f.getBoundingClientRect();return {url:f.getAttribute('src'),area:Math.max(0,r.width)*Math.max(0,r.height)}}).filter(x=>x.url&&/^https:\/\//i.test(x.url)).sort((a,b)=>b.area-a.area);
 if(!frames.length)return {ok:false};
 try{return {ok:true,url:new URL(frames[0].url,document.baseURI).href}}catch(e){return {ok:false}}
}
window.__zoomPlay={version:1,focus,exit,play,pause,toggle,seek,goTo,setZoom,setFit,setSpeed,next,status,frameInfo};
})();