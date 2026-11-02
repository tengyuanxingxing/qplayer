const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('node:fs'),vm=require('node:vm');
function search(deps) {
  const source=fs.readFileSync(__dirname+'/qqmusic/src/main.js','utf8');
  const context=vm.createContext({...deps,REF_SEARCH:'https://y.qq.com/',UA_MOBILE:'test',photoUrl:(type,mid)=>'https://y.gtimg.cn/'+mid});
  vm.runInContext(source.slice(source.indexOf('function searchArtists(args)'),source.indexOf('function songDetails(args)')),context);
  return context.searchArtists;
}
test('QQ artist search uses canonical singer mids and preserves pagination',async()=>{
  const fn=search({musicu:async args=>{assert.equal(args.search.param.search_type,1);assert.equal(args.search.param.page_num,2);return {search:{data:{body:{singer:{list:[{singerMID:'singer-mid',singerName:'音乐人',songNum:100,albumNum:12}]}},meta:{sum:30}}}};}});
  const page=await fn({query:'音乐人',cursor:'10',limit:10});
  assert.equal(page.items[0].id,'singer-mid');assert.equal(page.items[0].songCount,100);assert.equal(page.nextCursor,'11');
});
test('an empty desktop search falls back to actual QQ artist suggestions',async()=>{
  const fn=search({musicu:async()=>({search:{data:{body:{singer:{list:[]}}}}}),httpGetJson:async url=>{
    assert.match(url,/c6\.y\.qq\.com\/splcloud\/fcgi-bin\/smartbox_new/);
    return {code:0,data:{singer:{itemlist:[{id:'264',mid:'000GDDuQ3sGQiT',name:'王菲'}]}}};
  }});
  const page=await fn({query:'王菲',limit:3});
  assert.equal(page.items[0].id,'000GDDuQ3sGQiT');assert.equal(page.items[0].name,'王菲');assert.equal(page.nextCursor,'');
});
