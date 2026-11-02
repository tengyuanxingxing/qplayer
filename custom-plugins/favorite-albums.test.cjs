const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
function handler(folder,deps){
 const source=fs.readFileSync(__dirname+'/'+folder+'/src/main.js','utf8');
 const start=source.indexOf('function favoriteAlbums(args)');
 const end=source.indexOf('function comments(args)',start);
 const ctx=vm.createContext(deps);vm.runInContext(source.slice(start,end),ctx);return ctx.favoriteAlbums;
}
test('QQ requests saved albums with encrypted account, advances actual rows and preserves same-title IDs',async()=>{
 let calls=[];
 const favorites=handler('qqmusic',{loadCookies:async()=>({musickey:'fixture',euin:'encrypted'}),musicKey:c=>c.musickey,
  albumDto:r=>({id:r.mid,name:r.name}),musicu:async body=>{
   assert.equal(body.albums.module,'music.musicasset.AlbumFavRead');assert.equal(body.albums.method,'CgiGetAlbumFavInfo');
   assert.equal(body.albums.param.euin,'encrypted');calls.push(body.albums.param.offset);
   return {albums:{code:0,data:{total:3,hasmore:body.albums.param.offset===0?1:0,v_list:body.albums.param.offset===0?
    [{mid:'a',name:'Same',songnum:12,v_singer:[{mid:'s',name:'Singer'}]},{mid:'b',name:'Same',songnum:8}]:[{mid:'c',name:'Final'}]}}};
  }});
 const first=await favorites({});assert.equal(first.items.length,2);assert.equal(first.items[0].trackCount,12);
 assert.notEqual(first.items[0].id,first.items[1].id);assert.equal(first.nextCursor,'2');
 const last=await favorites({cursor:first.nextCursor});assert.equal(last.nextCursor,'');assert.deepEqual(calls,[0,2]);
});
test('QQ denied favorites cannot become a successful empty library',async()=>{
 const favorites=handler('qqmusic',{loadCookies:async()=>({euin:'x'}),musicKey:()=>true,musicu:async()=>({albums:{code:1000}})});
 await assert.rejects(favorites({}),/收藏专辑加载失败/);
});
test('NetEase uses saved album collection, cursor and account status',async()=>{
 const favorites=handler('netease',{albumDto:r=>({id:String(r.id),name:r.name}),weapi:async(path,args)=>{
  assert.equal(path,'album/sublist');assert.equal(args.offset,100);assert.equal(args.limit,100);
  return {code:200,hasMore:true,data:[{id:45,name:'Saved album'}]};
 }});
 const page=await favorites({cursor:'100'});assert.equal(page.items[0].id,'45');assert.equal(page.nextCursor,'101');
 const denied=handler('netease',{weapi:async()=>({code:301})});await assert.rejects(denied({}));
});
