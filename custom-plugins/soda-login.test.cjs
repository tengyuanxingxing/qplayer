const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm'),crypto=require('node:crypto');
function runtime(mode='success',savedCredentials) {
  const storage=new Map(), credentials=savedCredentials||new Map(), deviceIds=[], accountDeviceIds=[];let checks=0;
  const context=vm.createContext({module:{exports:{}},console,URL,Date,Promise,qplayer:{call:async(method,args)=>{
    if(method==='storage.get')return storage.get(args.key)||'';
    if(method==='storage.put'){storage.set(args.key,args.value);return true;}
    if(method==='credentials.get')return credentials.get(args.key)||'';
    if(method==='credentials.put'){credentials.set(args.key,args.value);return true;}
    if(method==='crypto.digest')return crypto.createHash(args.algorithm.toLowerCase()).update(args.data).digest(args.outputEncoding);
    if(method==='http.request') {
      const url=new URL(args.url);
      if(url.pathname.includes('get_qrcode')){deviceIds.push(url.searchParams.get('device_id'));return {status:200,body:JSON.stringify({data:{token:'test-token',qrcode_index_url:'https://api.qishui.com/test-qr'}}),setCookies:['pending=1; Path=/']};}
      if(url.pathname.includes('check_qrconnect')){checks++;deviceIds.push(url.searchParams.get('device_id'));return {status:200,body:JSON.stringify({data:mode==='expired'?{status:3}:{status:2,redirect_url:mode==='unsafe'?'https://unrelated.example/login':'https://api.qishui.com/test-redirect'}}),setCookies:[]};}
      if(url.pathname==='/test-redirect')return {status:302,headers:{location:'/test-complete'},setCookies:['sid_tt=authorized; Path=/']};
      if(url.pathname==='/test-complete'){assert.match(args.headers.Cookie,/sid_tt=authorized/);return {status:200,headers:{},setCookies:['sessionid=confirmed; Path=/']};}
      if(url.pathname==='/luna/pc/me'){accountDeviceIds.push(url.searchParams.get('device_id'));if(mode==='account-error')throw Error('offline');return {status:200,body:JSON.stringify({my_info:{id:'test-user',nickname:'Test'}})};}
      throw Error('Unexpected URL path '+url.pathname);
    }
    throw Error('Unexpected host method '+method);
  }}});
  vm.runInContext(fs.readFileSync(__dirname+'/soda/src/main.js','utf8'),context);
  return {handlers:context.module.exports.handlers,credentials,deviceIds,accountDeviceIds,get checks(){return checks;}};
}
test('confirmed numeric QR state follows the login redirects and preserves challenge device identity',async()=>{
  const r=runtime();await r.handlers.login({operation:'begin',methodId:'qr'});
  await new Promise(resolve=>setTimeout(resolve,5));
  const result=await r.handlers.login({operation:'poll',challengeId:'test-token'});
  assert.equal(result.status,'success');assert.equal(result.account.loggedIn,true);
  assert.equal(r.deviceIds[0],r.deviceIds[1]);
  assert.equal(JSON.parse(r.credentials.get('cookies')).sessionid,'confirmed');
  assert.equal(r.accountDeviceIds[0],r.deviceIds[0]);
  await new Promise(resolve=>setTimeout(resolve,5));
  const restarted=runtime('success',r.credentials);
  assert.equal((await restarted.handlers.account({})).loggedIn,true);
  assert.equal(restarted.accountDeviceIds[0],r.deviceIds[0]);
});
test('receiving a session alone never fabricates a successful account',async()=>{
  const r=runtime('account-error');await r.handlers.login({operation:'begin',methodId:'qr'});
  const result=await r.handlers.login({operation:'poll',challengeId:'test-token'});
  assert.equal(result.status,'scanned');
  const again=await r.handlers.login({operation:'poll',challengeId:'test-token'});
  assert.equal(again.status,'scanned');assert.equal(r.checks,1);
});
test('expired numeric QR state is reported, and unrelated redirect domains are rejected',async()=>{
  const r=runtime('expired');await r.handlers.login({operation:'begin',methodId:'qr'});
  assert.equal((await r.handlers.login({operation:'poll',challengeId:'test-token'})).status,'expired');
  const unsafe=runtime('unsafe');await unsafe.handlers.login({operation:'begin',methodId:'qr'});
  await assert.rejects(unsafe.handlers.login({operation:'poll',challengeId:'test-token'}));
  assert.equal(unsafe.credentials.size,0);
});
