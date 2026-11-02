const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');

function plugin(reply) {
  const context=vm.createContext({module:{exports:{}},require:path=>
    path==='./together'?{create:()=>({})}:{},qplayer:{call:()=>{throw Error('unexpected host call');}}});
  vm.runInContext(fs.readFileSync(__dirname+'/netease/src/main.js','utf8'),context);
  context.weapi=async(path,args)=>{
    assert.equal(path,'v1/album/32311');assert.equal(Object.keys(args).length,0);
    return reply;
  };
  return context.module.exports.handlers.albumDetails;
}
function song(id,no,cd) {return {id,name:'Song '+id,no,cd,ar:[{id:7,name:'Artist'}],al:{id:32311,name:'Album'}};}
function ids(album) {return Array.from(album.songs,s=>s.id);}

test('NetEase album details expose company, native category and complete multiline introduction',async()=>{
  // Field names confirmed against the public /api/v1/album/32311 response.
  const details=await plugin({album:{id:32311,name:'神的游戏',company:'索尼音乐',
    type:'专辑',subType:'录音室版',description:'第一段。\n\n第二段。',publishTime:1344528000000,size:9},
    songs:[song(326696,2,'1'),song(326697,3,'1')]})({id:'32311'});
  assert.equal(details.company,'索尼音乐');assert.equal(details.releaseType,'专辑');
  assert.equal(details.releaseTypeLabel,'录音室版');assert.equal(details.description,'第一段。\n\n第二段。');
  assert.equal(details.publishTimeMs,1344528000000);assert.equal(details.trackCount,9);
  assert.deepEqual(ids(details),['326696','326697']); // unavailable track 1 is not invented
});

test('missing metadata stays empty, short introduction is a fallback and native type remains usable',async()=>{
  const details=await plugin({album:{id:32311,type:'EP',briefDesc:'短介绍\n第二行'},songs:[]})({id:'32311'});
  assert.equal(details.company,'');assert.equal(details.description,'短介绍\n第二行');
  assert.equal(details.releaseType,'EP');assert.equal(details.releaseTypeLabel,'EP');
  const invalid=await plugin({album:{id:32311,company:{name:'not a native string'},description:[]},songs:[]})({id:'32311'});
  assert.equal(invalid.company,'');assert.equal(invalid.description,'');
});

test('real disc and track indices determine release order; equal indices retain API order',async()=>{
  const songs=[song(1,2,'2'),song(90,2,'1'),song(89,2,'1'),song(50,1,'1'),song(7,1,'2')];
  const before=songs.map(s=>s.id);
  const details=await plugin({album:{id:32311},songs})({id:'32311'});
  assert.deepEqual(ids(details),['50','90','89','7','1']);
  assert.deepEqual(songs.map(s=>s.id),before,'the provider response array is not mutated');
});

test('no disc markers permits track order, but partial or malformed indices preserve original API order',async()=>{
  const indexed=await plugin({album:{id:32311},songs:[song(90,2),song(50,1)]})({id:'32311'});
  assert.deepEqual(ids(indexed),['50','90']);
  for(const rows of [
    [song(90,2,'1'),song(50,undefined,'1')],
    [song(90,2,'1'),song(50,1)],
    [song(90,2,'CD1'),song(50,1,'CD1')],
    [song(90,0,'1'),song(50,1,'1')],
    [song(90,undefined),song(50,undefined)]
  ]) {
    const details=await plugin({album:{id:32311},songs:rows})({id:'32311'});
    assert.deepEqual(ids(details),['90','50']);
  }
});

test('NetEase metadata plugin declares its matching host contract',()=>{
  const manifest=JSON.parse(fs.readFileSync(__dirname+'/netease/plugin.json','utf8'));
  assert.equal(manifest.version,'0.5.8.6');assert.equal(manifest.minHostVersion,'1.9.19-custom');
});
