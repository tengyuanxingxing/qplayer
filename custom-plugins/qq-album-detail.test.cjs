const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
function plugin(respond){
 const context=vm.createContext({module:{exports:{}},require:()=>({}),qplayer:{call:async(method,args)=>{
  if(method==='credentials.get')return null;
  assert.equal(method,'http.request');return {status:200,body:JSON.stringify(await respond(JSON.parse(args.body)))};
 }}});
 vm.runInContext(fs.readFileSync(__dirname+'/qqmusic/src/main.js','utf8'),context);return context;
}
function track(mid,disc,index){return {songInfo:{mid,title:mid,index_cd:disc,index_album:index}};}
test('QQ album uses canonical detail parameters and returns disc/track order plus original metadata',async()=>{
 const p=plugin(body=>{
  assert.equal(body.info.param.albumMId,'albumMid');assert.equal(body.songs.param.albumMid,'albumMid');
  assert.equal(Object.hasOwn(body.songs.param,'order'),false);
  return {info:{code:0,data:{basicInfo:{albumMid:'albumMid',albumName:'Album',albumType:'录音室专辑',desc:'Original intro',publishDate:'2025-01-02'},company:{name:'Original label'},singer:{singerList:[{singerMid:'artistMid',singerName:'Artist'}]}}},
   songs:{code:0,data:{totalNum:4,songList:[track('d2t2',2,2),track('d1t2',1,2),track('d2t1',2,1),track('d1t1',1,1)]}}};
 });
 const result=await p.albumDetails({id:'albumMid'});
 assert.deepEqual(Array.from(result.songs,s=>s.id),['d1t1','d1t2','d2t1','d2t2']);
 assert.equal(result.company,'Original label');assert.equal(result.description,'Original intro');
 assert.equal(result.releaseTypeLabel,'录音室专辑');assert.equal(result.artists[0].id,'artistMid');assert.equal(result.trackCount,4);
});
test('QQ album accumulates every page before sorting, including a single-object page',async()=>{
 const starts=[];
 const p=plugin(body=>{
  const start=body.songs.param.begin;starts.push(start);
  const songs={code:0,data:{totalNum:3,songList:start===0?[track('third',0,3),track('first',0,1)]:track('second',0,2)}};
  return body.info?{info:{code:0,data:{basicInfo:{albumMid:'mid',name:'Album'}}},songs}:{songs};
 });
 const result=await p.albumDetails({id:'mid'});assert.deepEqual(starts,[0,2]);
 assert.deepEqual(Array.from(result.songs,s=>s.id),['first','second','third']);
});
test('missing indexes retain interface slots and equal indexes retain their relative order',()=>{
 const p=plugin(()=>{});
 const missing=[track('z',0,0),track('a',0,undefined),track('m',0,0)];
 assert.deepEqual(Array.from(p.albumTrackOrder(missing),r=>r.songInfo.mid),['z','a','m']);
 const mixed=[track('third',0,3),track('unknown',0,0),track('firstA',0,1),track('firstB',0,1)];
 assert.deepEqual(Array.from(p.albumTrackOrder(mixed),r=>r.songInfo.mid),['firstA','unknown','firstB','third']);
});
test('denied or incomplete QQ album pages fail instead of returning a shuffled partial album',async()=>{
 const denied=plugin(()=>({info:{code:1000},songs:{code:0,data:{songList:[]}}}));
 await assert.rejects(denied.albumDetails({id:'mid'}),/专辑加载失败/);
 const missing=plugin(body=>body.info?{info:{code:0,data:{basicInfo:{name:'Album'}}},songs:{code:0,data:{totalNum:2,songList:[track('first',0,1)]}}}:{songs:{code:0,data:{songList:[]}}});
 await assert.rejects(missing.albumDetails({id:'mid'}),/未完整返回/);
});
