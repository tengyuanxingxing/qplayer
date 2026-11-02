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
  return {home:context.home,requests};
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
