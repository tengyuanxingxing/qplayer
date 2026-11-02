const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');

function plugin(respond) {
  const requests=[];
  const context=vm.createContext({module:{exports:{}},require:()=>({qrcDecrypt:()=>''}),qplayer:{call:async(method,args)=>{
    if(method==='credentials.get')return JSON.stringify(args.key==='cookies'?{musicid:'123',musickey:'fixture-key',tmeLoginType:2}:{});
    if(method==='storage.get')return null;
    if(method==='storage.put')return true;
    if(method==='http.request'){
      const body=JSON.parse(args.body);requests.push(body);
      assert.match(args.headers.Cookie,/musickey=fixture-key/);
      assert.equal(body.comm.uin,'123');assert.equal(body.comm.authst,'fixture-key');assert.equal(body.comm.tmeLoginType,2);
      return {status:200,body:JSON.stringify(await respond(body))};
    }
    throw Error('unexpected '+method);
  }}});
  vm.runInContext(fs.readFileSync(__dirname+'/qqmusic/src/main.js','utf8'),context);
  return {home:context.home,like:context.like,songDetails:context.songDetails,requests};
}

test('QQ home returns playable personal-radio tracks using the QQ account, independently of playlist failures',async()=>{
  const p=plugin(body=>{
    if(!body.guess)throw Error('playlist endpoint offline');
    assert.equal(body.comm.ct,11);
    assert.equal(body.guess.module,'music.radioProxy.MbTrackRadioSvr');
    assert.equal(body.guess.method,'get_radio_track');assert.equal(body.guess.param.id,99);
    return {guess:{code:0,data:{tracks:[{track_info:{mid:'mid-1',title:'推荐歌曲',singer:[{mid:'artist-mid',name:'歌手'}],album:{pmid:'art'},interval:180}},{mid:'mid-1'},{id:123}]}}};
  });
  const result=await p.home({limit:12});
  assert.equal(result.songs.length,1);assert.equal(result.songs[0].id,'mid-1');
  assert.equal(result.songs[0].title,'推荐歌曲');assert.equal(result.songs[0].durationMs,180000);
  assert.equal(result.playlists.length,0);
});

test('denied radio never fabricates music or consumes playlist content as songs',async()=>{
  const p=plugin(body=>body.guess?{guess:{code:1101,data:{tracks:null}}}:{recomPlaylist:{data:{v_hot:[]}}});
  const result=await p.home({limit:12});assert.equal(result.songs.length,0);
});

test('radio recommendations fill ten three-row groups even when the service caps each batch',async()=>{
  let calls=0;
  const p=plugin(body=>{
    if(!body.guess)throw Error('playlist endpoint offline');
    const excluded=body.guess.param.song_ids;
    assert.equal(excluded.length,calls*5);
    assert.equal(body.guess.param.num,30);
    const start=calls++*5;
    return {guess:{code:0,data:{tracks:Array.from({length:5},(_,i)=>({track_info:{mid:'r'+(start+i),id:start+i+1,title:'Radio '+(start+i)}}))}}};
  });
  const result=await p.home({limit:3});
  assert.equal(result.songs.length,30);assert.equal(calls,6);
  assert.equal(result.songs[29].id,'r29');
});

test('repeated batches stop without padding duplicates; later network failures preserve real results',async()=>{
  let calls=0;
  const p=plugin(body=>{
    if(!body.guess)throw Error('playlist endpoint offline');calls++;
    return {guess:{data:{tracks:[{mid:'same',title:'one real song'}]}}};
  });
  assert.equal((await p.home({limit:12})).songs.length,1);assert.equal(calls,2);
  let attempts=0;
  const broken=plugin(body=>{
    if(!body.guess || attempts++>0)throw Error('network failed');
    return {guess:{data:{tracks:[{mid:'real',title:'real song'}]}}};
  });
  assert.equal((await broken.home({limit:12})).songs[0].id,'real');
});


test('1018 favorites advance by actual page size, reuse complete metadata and retain exact playlist order',async()=>{
  const offsets=[];let details=0;
  const p=plugin(body=>{
    if(body.mine)return {mine:{data:{v_playlist:[{dirId:201,tid:1234}]}}};
    if(body.req && body.req.method==='uniform_get_Dissinfo'){
      const start=body.req.param.song_begin;offsets.push(start);
      const length=Math.min(200,1018-start);
      return {req:{code:0,data:{dirinfo:{songnum:1018},songlist:Array.from({length},(_,i)=>({mid:'m'+(start+i),id:start+i+1,title:'歌曲'+(start+i),singer:[],interval:180}))}}};
    }
    details++;throw Error('cached favorite must not refetch song details');
  });
  const ids=await p.like({operation:'list'});assert.equal(ids.length,1018);assert.equal(ids[1017],'m1017');
  assert.deepEqual(offsets,[0,200,400,600,800,1000]);
  let resolved=[];for(let i=0;i<ids.length;i+=100)resolved.push(...await p.songDetails({ids:ids.slice(i,i+100)}));
  assert.equal(resolved.length,1018);assert.equal(resolved[1017].id,'m1017');assert.equal(details,0);
});

test('uncached QQ metadata never sends more than ten modules per request',async()=>{
  const sizes=[];const p=plugin(body=>{
    const keys=Object.keys(body).filter(k=>k.startsWith('req_'));sizes.push(keys.length);
    assert.ok(keys.length<=10);const response={};for(const key of keys)response[key]={data:{track_info:{mid:body[key].param.song_mid,title:'歌曲'}}};return response;
  });
  const mids=Array.from({length:118},(_,i)=>'m'+i);const songs=await p.songDetails({ids:mids});
  assert.equal(songs.length,118);assert.equal(sizes.length,12);assert.equal(sizes.at(-1),8);
});
