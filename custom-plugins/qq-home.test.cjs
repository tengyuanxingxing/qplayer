const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');

function plugin(respond,identity={code:0,data:{info:{uin:'123'}}},cookies={musicid:'123',musickey:'fixture-key',tmeLoginType:2}) {
  const requests=[];
  const context=vm.createContext({module:{exports:{}},require:()=>({qrcDecrypt:()=>''}),qplayer:{call:async(method,args)=>{
    if(method==='credentials.get')return JSON.stringify(args.key==='cookies'?cookies:{});
    if(method==='storage.get')return null;
    if(method==='storage.put')return true;
    if(method==='http.request'){
      if(args.method==='GET')return {status:200,body:JSON.stringify(await respond({webIdentity:true,request:args}))};
      const body=JSON.parse(args.body);requests.push(body);
      if(body.identity)return {status:200,body:JSON.stringify(typeof identity==='function'?await identity(body):{identity})};
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
    assert.equal(body.comm.ct,19);assert.equal(body.comm.cv,0);
    assert.equal(body.guess.module,'music.radioProxy.MbTrackRadioSvr');
    assert.equal(body.guess.method,'get_radio_track');assert.equal(body.guess.param.id,99);assert.equal(body.guess.param.num,5);
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
    assert.equal(body.comm.ct,19);
    assert.equal(body.guess.param.id,99);assert.equal(body.guess.param.num,5);
    const start=calls++*5;
    return {guess:{code:0,data:{tracks:Array.from({length:5},(_,i)=>({track_info:{mid:'r'+(start+i),id:start+i+1,title:'Radio '+(start+i)}}))}}};
  });
  const result=await p.home({limit:3});
  assert.equal(result.songs.length,30);assert.equal(calls,6);
  assert.equal(result.songs[29].id,'r29');
});

test('repeated batches stop without padding duplicates; later network failures expose an error instead of a partial success',async()=>{
  let calls=0;
  const p=plugin(body=>{
    if(!body.guess)throw Error('playlist endpoint offline');calls++;
    return {guess:{data:{tracks:[{mid:'same',title:'one real song'}]}}};
  });
  assert.equal((await p.home({limit:12})).songs.length,1);assert.equal(calls,3);
  let attempts=0;
  const broken=plugin(body=>{
    if(!body.guess || attempts++>0)throw Error('network failed');
    return {guess:{data:{tracks:[{mid:'real',title:'real song'}]}}};
  });
  const failed=await broken.home({limit:12});
  assert.equal(failed.songs.length,0);assert.match(failed.recommendationStatus,/network failed/);
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

// The documented account radio endpoint is allowed to cap each response at five.
test('authenticated account feed retains new batches and skips a repeated batch',async()=>{
  let calls=0;
  const p=plugin(body=>{
    if(!body.guess)throw Error('no playlists');
    assert.equal(body.comm.ct,19);assert.equal(body.guess.param.id,99);
    const batch=calls++; const start=(batch===1?0:batch===0?0:batch-1)*5;
    return {guess:{data:{track:Array.from({length:5},(_,i)=>({mid:'account'+(start+i),title:'personal'}))}}};
  });
  const songs=(await p.home({})).songs;
  assert.equal(songs.length,30);assert.equal(new Set(songs.map(s=>s.id)).size,30);
  assert.equal(calls,7);assert.ok(songs.every(s=>s.id.startsWith('account')));
});

test('QQ QR GET omits optional body even through JSON bridge serialization',async()=>{
  const context=vm.createContext({module:{exports:{}},require:()=>({}),qplayer:{call:async(method,args)=>{
    assert.equal(method,'http.request');const wire=JSON.parse(JSON.stringify(args));
    assert.equal(wire.method,'GET');assert.equal(Object.hasOwn(wire,'body'),false);
    return {status:200,body:'fixture'};
  }}});
  vm.runInContext(fs.readFileSync(__dirname+'/qqmusic/src/main.js','utf8'),context);
  const result=await context.httpRaw('GET','https://ssl.ptlogin2.qq.com/ptqrshow',{});
  assert.equal(result.status,200);
});

test('QQ authorization preserves the encrypted account id required by album collections',async()=>{
  let cookies;
  const context=vm.createContext({module:{exports:{}},require:()=>({}),qplayer:{call:async(method,args)=>{
    if(method==='http.request')return {status:200,body:JSON.stringify({req_1:{data:{musicid:123,musickey:'fixture',encryptUin:'encrypted-account'}}})};
    if(method==='credentials.put'){if(args.key==='cookies')cookies=JSON.parse(args.value);return true;}
    throw Error('unexpected '+method);
  }}});
  vm.runInContext(fs.readFileSync(__dirname+'/qqmusic/src/main.js','utf8'),context);
  const profile=await context.qqExchangeCode('fixture-code');
  assert.equal(profile.loggedIn,true);assert.equal(cookies.euin,'encrypted-account');
});

test('invalid or mismatched account cannot silently become anonymous recommendations',async()=>{
 for(const identity of [{code:1000},{code:0,data:{info:{uin:'999'}}},{code:0,data:{info:{nick:'No identity'}}},{code:0,data:{info:{uin:'0'}}}]){
  let radio=0;const p=plugin(body=>{if(body.guess)radio++;return {};},identity);
  const home=await p.home({});assert.equal(home.songs.length,0);assert.match(home.recommendationStatus,/未通过|账号不一致/);assert.equal(radio,0);
 }
});

test('Web login verifies its self homepage when the eas_sid-dependent nickname endpoint is unavailable',async()=>{
 let self=0,radio=0;
 const p=plugin(body=>{
  if(body.webIdentity){self++;assert.match(body.request.url,/hostUin=0/);assert.match(body.request.headers.Cookie,/musickey=fixture-key/);return {code:0,data:{profile:{uin:'123',nick:'fixture'}}};}
  if(body.guess){radio++;return {guess:{data:{tracks:[{mid:'verified',title:'real'}]}}};}
  throw Error('no daily');
 },{code:1000});
 const home=await p.home({});assert.equal(self,1);assert.ok(radio>0);assert.equal(home.songs[0].id,'verified');
 assert.equal(p.requests[0].comm.ct,24);assert.equal(p.requests[0].nativeIdentity.method,'GetUserInfo');
});

test('native self identity can be at the data root rather than the nickname info envelope',async()=>{
 const p=plugin(body=>body.guess?{guess:{data:{tracks:[{mid:'native',title:'real'}]}}}:{daily:{code:1000}},
  ()=>({identity:{code:1000},nativeIdentity:{code:0,data:{str_musicid:'123',nick:'fixture'}}}));
 const h=await p.home({});assert.equal(h.songs[0].id,'native');
});

test('mismatched self homepage and public-looking nickname-only data cannot enable radio',async()=>{
 for(const profile of [{uin:'999'},{nick:'public looking profile'}]){
  let radio=0;const p=plugin(body=>{if(body.webIdentity)return {code:0,data:{profile}};if(body.guess)radio++;return {};},{code:1000});
  const h=await p.home({});assert.equal(h.songs.length,0);assert.equal(radio,0);assert.match(h.recommendationStatus,/未通过|不一致/);
 }
});

test('a stale preferred key is replaced only after another alias verifies the same self identity',async()=>{
 let verified=false;
 const p=plugin(body=>{
  if(body.webIdentity)return {code:1000};
  if(body.guess){assert.ok(verified);assert.equal(body.comm.authst,'fixture-key');return {guess:{data:{tracks:[{mid:'correct',title:'real'}]}}};}
  return {daily:{code:1000}};
 },body=>{
  if(body.comm.authst==='old-key')return {identity:{code:1000},nativeIdentity:{code:1000}};
  assert.equal(body.comm.authst,'fixture-key');verified=true;return {nativeIdentity:{code:0,data:{str_musicid:'123'}}};
 },{musicid:'123',qm_keyst:'old-key',musickey:'fixture-key',tmeLoginType:2});
 const h=await p.home({});assert.equal(h.songs[0].id,'correct');assert.equal(p.requests.filter(x=>x.identity).length,2);
});

test('expired native sessions renew once and must pass self identity verification again',async()=>{
 for(const outcome of ['valid','stillExpired','mismatched']){
  let cookies={musicid:'123',musickey:'expired',psrf_qqrefresh_token:'fixture-refresh'},renews=0,radios=0,newChecks=0;
  const context=vm.createContext({module:{exports:{}},require:()=>({}),qplayer:{call:async(method,args)=>{
   if(method==='credentials.get')return JSON.stringify(args.key==='cookies'?cookies:{});
   if(method==='credentials.put'){if(args.key==='cookies')cookies=JSON.parse(args.value);return true;}
   if(method!=='http.request')throw Error(method);
   if(args.method==='GET')return {status:200,body:JSON.stringify({code:1000})};
   const body=JSON.parse(args.body);let response;
   if(body.req && body.req.method==='Login'){renews++;response={req:{code:0,data:{musicid:123,musickey:'renewed'}}};}
   else if(body.identity){
    if(body.comm.authst==='renewed')newChecks++;
    response=body.comm.authst!=='renewed'||outcome==='stillExpired'?{identity:{code:1000},nativeIdentity:{code:1000}}:
     {nativeIdentity:{code:0,data:{str_musicid:outcome==='mismatched'?'999':'123'}}};
   } else if(body.guess){radios++;response={guess:{code:0,data:{tracks:[{mid:'renewed-track',title:'real'}]}}};}
   else response={daily:{code:0,data:{v_shelf:[]}}};
   return {status:200,body:JSON.stringify(response)};
  }}});
  vm.runInContext(fs.readFileSync(__dirname+'/qqmusic/src/main.js','utf8'),context);
  const home=await context.home({});assert.equal(renews,1,outcome);assert.equal(newChecks,1,outcome);
  if(outcome==='valid'){assert.ok(radios>0);assert.equal(home.songs[0].id,'renewed-track');}
  else {assert.equal(radios,0);assert.equal(home.songs.length,0);}
 }
});

test('an explicit self identity mismatch never triggers credential renewal',async()=>{
 let renews=0;
 const p=plugin(body=>{if(body.req && body.req.method==='Login')renews++;return {};},{code:0,data:{info:{uin:'999'}}});
 const result=await p.home({});assert.equal(renews,0);assert.match(result.recommendationStatus,/不一致/);
});
test('daily card uses a unique current-day dynamic playlist, not radio substitution',async()=>{
 const now=Math.floor(Date.now()/1000);let id;
 const p=plugin(body=>{
  if(body.guess)return {guess:{data:{tracks:[]}}};
  if(body.daily)return {daily:{code:0,data:{v_shelf:[{v_niche:[{v_card:[{subtype:510,id:'987654',time:now}]}]}]}}};
  if(body.req){assert.equal(body.req.method,'CgiGetDiss');id=body.req.param.disstid;return {req:{data:{dirinfo:{songnum:1},songlist:[{mid:'daily',title:'daily'}]}}};}
  throw Error('unexpected');
 });
 const h=await p.home({});assert.equal(id,987654);assert.equal(h.dailySongs[0].id,'daily');assert.equal(h.songs.length,0);
});
test('old daily card is rejected without replacing it by a public playlist or radio',async()=>{
 const p=plugin(body=>body.guess?{guess:{data:{tracks:[]}}}:{daily:{data:{v_shelf:[{v_niche:[{v_card:[{subtype:510,id:'987',time:1}]}]}]}}});
 const h=await p.home({});assert.equal(h.dailySongs.length,0);assert.match(h.dailyStatus,/过期/);
});

test('modern QQ web keys and WeChat account IDs take priority over stale aliases',()=>{
 const context=vm.createContext({module:{exports:{}},require:()=>({}),qplayer:{}});
 vm.runInContext(fs.readFileSync(__dirname+'/qqmusic/src/main.js','utf8'),context);
 assert.equal(context.musicKey({qm_keyst:'current',musickey:'old'}),'current');
 assert.equal(context.musicUin({login_type:2,wxuin:'o00123',uin:'999'}),'123');
 assert.equal(context.musicUin({musicid:'123',uin:'999'}),'123');
});
