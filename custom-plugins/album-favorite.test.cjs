const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');

function albumHandlers(folder,deps){
  const source=fs.readFileSync(__dirname+'/'+folder+'/src/main.js','utf8');
  const start=source.indexOf('function favoriteAlbums(args)');
  const end=source.indexOf('function comments(args)',start);
  const ctx=vm.createContext(deps);vm.runInContext(source.slice(start,end),ctx);return ctx;
}
function qq(deps={}){
  return albumHandlers('qqmusic',Object.assign({requirePersonalAccount:async()=> '123',
    loadCookies:async()=>({musicid:'123',musickey:'test'}),musicUin:c=>c.musicid,
    musicKey:c=>c.musickey,albumDto:r=>({id:r.mid,name:r.name}),WRITE_COMM:'write'},deps));
}
test('QQ album write resolves MID, uses numeric album ID and reads collection back',async()=>{
  let saved=false;const calls=[];
  const p=qq({musicu:async(body,comm)=>{
    calls.push(body);
    if(body.albumId){assert.equal(body.albumId.param.albumMId,'nativeMid');return {albumId:{code:0,data:{basicInfo:{albumID:42,albumMid:'nativeMid'}}}};}
    if(body.writeAlbum){assert.equal(comm,'write');assert.equal(body.writeAlbum.module,'music.musicasset.AlbumFavWrite');
      assert.deepEqual(Array.from(body.writeAlbum.param.v_albumId),[42]);saved=body.writeAlbum.method==='FavAlbum';return {writeAlbum:{code:0,data:{retcode:0}}};}
    return {albums:{code:0,data:{v_list:saved?[{mid:'nativeMid',name:'Saved'}]:[]}}};
  }});
  assert.equal((await p.albumFavorite({id:'nativeMid',operation:'add'})).favorited,true);
  assert.equal((await p.albumFavorite({id:'nativeMid',operation:'remove'})).favorited,false);
  assert.equal(calls.filter(c=>c.writeAlbum).length,2);
  assert.equal(calls.filter(c=>c.albums).length,2);
});
test('QQ numeric IDs are canonicalized to MID for verified collection status',async()=>{
  const p=qq({musicu:async body=>{
    if(body.albumId){assert.equal(body.albumId.param.albumId,42);return {albumId:{code:0,data:{basicInfo:{albumID:42,albumMid:'nativeMid'}}}};}
    if(body.writeAlbum)throw Error('status must not write');
    const offset=body.albums.param.offset;
    return {albums:{code:0,data:{hasmore:offset===0?1:0,v_list:offset===0?[{mid:'other',name:'Same'}]:[{mid:'nativeMid',name:'Same'}]}}};
  }});
  assert.equal((await p.albumFavorite({id:'42',operation:'status'})).favorited,true);
});
test('QQ denied identity prevents album writes; missing write code and unconfirmed readback are failures',async()=>{
  const denied=qq({requirePersonalAccount:async()=>{throw Error('login denied');},musicu:async()=>{throw Error('must not request');}});
  await assert.rejects(denied.albumFavorite({id:'mid',operation:'add'}),/login denied/);
  for(const reply of [{writeAlbum:{}},{writeAlbum:{code:1000}},{writeAlbum:{code:0,data:{retCode:42}}},{writeAlbum:{code:0}}]){
    const p=qq({musicu:async body=>body.albumId?{albumId:{code:0,data:{basicInfo:{albumID:42,albumMid:'mid'}}}}:
      body.writeAlbum?reply:{albums:{code:0,data:{v_list:[]}}}});
    await assert.rejects(p.albumFavorite({id:'mid',operation:'add'}),/未确认|尚未确认/);
  }
});
test('QQ account changes between album resolution and write are rejected',async()=>{
  const p=qq({loadCookies:async()=>({musicid:'different'}),musicu:async body=>{
    assert.ok(body.albumId);return {albumId:{code:0,data:{basicInfo:{albumID:42,albumMid:'mid'}}}};
  }});
  await assert.rejects(p.albumFavorite({id:'mid',operation:'add'}),/账号已变化/);
});
test('NetEase album add and remove use native subscription endpoints and verify server state',async()=>{
  let saved=false;const paths=[];
  const p=albumHandlers('netease',{albumDto:r=>({id:String(r.id)}),weapi:async(path,args)=>{
    paths.push(path);
    if(path==='album/sub' || path==='album/unsub'){assert.equal(args.id,'42');saved=path==='album/sub';return {code:200};}
    assert.equal(path,'album/sublist');return {code:200,data:saved?[{id:42}]:[]};
  }});
  assert.equal((await p.albumFavorite({id:'42',operation:'add'})).favorited,true);
  assert.equal((await p.albumFavorite({id:'42',operation:'remove'})).favorited,false);
  assert.deepEqual(paths,['album/sub','album/sublist','album/unsub','album/sublist']);
});
test('NetEase denied writes and unconfirmed collections never report success',async()=>{
  for(const code of [301,200]){
    const p=albumHandlers('netease',{albumDto:r=>r,weapi:async(path)=>({code:path==='album/sublist'?200:code,data:[]})});
    await assert.rejects(p.albumFavorite({id:'42',operation:'add'}),/未确认|尚未确认/);
  }
});
test('invalid album operations cannot cause a platform write',async()=>{
  for(const folder of ['qqmusic','netease']){
    const p=folder==='qqmusic'?qq():albumHandlers(folder,{});
    assert.throws(()=>p.albumFavorite({id:'x:y',operation:'add'}),/专辑 ID/);
    assert.throws(()=>p.albumFavorite({id:'42',operation:'invalid'}),/未知/);
  }
});
