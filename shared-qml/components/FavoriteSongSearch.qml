import QtQuick
import miuix.Core
import "."

// Search and facets operate on the currently opened collection, never the catalog.
Rectangle {
    id: search
    objectName:"favoriteSongSearch"
    property real bottomContentInset:0
    signal close()
    color:Theme.color.surface
    property string query:""
    onQueryChanged:if(queryInput.text!==search.query)queryInput.text=search.query
    property string artist:""
    property string genre:""
    property string decade:""
    property string provider:""
    property int artistLimit:12
    property var groups: {
        var tracks=player.sourcePlaylistTracks
        var legacy=player.playlistTracks
        return search.visible?player.favoriteSongFilterGroups():[]
    }
    property var results: {
        var tracks=player.sourcePlaylistTracks
        var legacy=player.playlistTracks
        return search.visible?player.filterFavoriteSongs(search.query,search.artist,search.genre,search.decade,search.provider):[]
    }
    function selected(key) {
        if(key==="artist")return search.artist
        if(key==="genre")return search.genre
        if(key==="decade")return search.decade
        if(key==="provider")return search.provider
        return ""
    }
    function choose(key,value) {
        var next=search.selected(key)===value?"":value
        if(key==="artist")search.artist=next
        if(key==="genre")search.genre=next
        if(key==="decade")search.decade=next
        if(key==="provider")search.provider=next
        resultsList.contentY=0
    }
    function reset() {
        queryInput.text=""
        search.query="";search.artist="";search.genre="";search.decade="";search.provider=""
        search.artistLimit=12;resultsList.contentY=0
    }
    function optionCount(group) {
        var options=group.options||[]
        return group.key==="artist"?Math.min(options.length,search.artistLimit):options.length
    }
    function groupHeight(group) {
        return 36+Math.ceil(search.optionCount(group)/3)*44+(group.key==="artist" && group.options.length>search.artistLimit?36:0)+16
    }
    function groupY(index) {
        var y=0
        for(var i=0;i<index;i++)y+=search.groupHeight(search.groups[i])
        return y
    }
    property real facetsHeight: {
        var h=0
        for(var i=0;i<search.groups.length;i++)h+=search.groupHeight(search.groups[i])
        return h
    }
    MouseArea { anchors.fill:parent }
    IconButton { objectName:"favoriteSearchBack";x:4;y:8;icon:"arrow_back";onClicked:search.close() }
    TextField {
        id:queryInput
        objectName:"favoriteSearchInput"
        x:52;y:8;width:search.width-136;height:48
        overlayField:true;label:"搜索我喜欢的歌曲";leadingIcon:"search"
        text:search.query
        onTextChanged:{search.query=text;resultsList.contentY=0}
    }
    Button { objectName:"favoriteSearchClear";anchors.right:parent.right;anchors.rightMargin:8;y:12;text:"清空";onClicked:search.reset() }
    VirtualSongList {
        id:resultsList
        objectName:"favoriteSearchResults"
        x:0;y:68;width:search.width;height:search.height-68
        bottomContentInset:search.bottomContentInset
        list:search.results
        headerHeight:search.facetsHeight+104
        header:Component {
            Item {
                width:resultsList.width;height:resultsList.headerHeight
                Repeater {
                    model:search.groups
                    Item {
                        property var group:modelData
                        x:16;y:search.groupY(index);width:resultsList.width-32;height:search.groupHeight(group)
                        Text { x:0;y:8;text:parent.group.title;font.pixelSize:17;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor }
                        Repeater {
                            model:search.optionCount(parent.group)
                            Rectangle {
                                property var group:parent.group
                                property var option:group.options[index]
                                objectName:"favoriteFilterChip"
                                x:(index%3)*((parent.width+8)/3);y:36+Math.floor(index/3)*44
                                width:(parent.width-16)/3;height:36;radius:18
                                color: {
                                    var a=search.artist;var g=search.genre;var d=search.decade;var p=search.provider
                                    return search.selected(group.key)===String(option.value)?Theme.color.primaryContainer:Theme.color.surfaceContainerHigh
                                }
                                Text { x:10;y:9;width:parent.width-20;text:parent.option.label+" "+parent.option.count;font.pixelSize:13;elide:Text.ElideRight;color:Theme.color.onSurfaceColor }
                                Ripple { anchors.fill:parent;onClicked:if(tapAllowed)search.choose(parent.group.key,String(parent.option.value)) }
                            }
                        }
                        Button {
                            visible:parent.group.key==="artist" && parent.group.options.length>search.artistLimit
                            x:0;y:36+Math.ceil(search.optionCount(parent.group)/3)*44
                            text:"更多歌手";onClicked:search.artistLimit+=12
                        }
                    }
                }
                Text { x:16;y:search.facetsHeight;width:parent.width-32;text:"按你的收藏生成，可组合筛选，再点一次取消选择";wrapMode:Text.Wrap;font.pixelSize:12;color:Theme.color.onSurfaceVariantSummary }
                Text { x:16;y:search.facetsHeight+48;text:"找到 "+search.results.length+" 首歌曲";font.pixelSize:17;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor }
                Text { x:16;y:search.facetsHeight+78;visible:search.results.length===0;text:"没有符合条件的歌曲，试试其他关键词或清空筛选";font.pixelSize:12;color:Theme.color.onSurfaceVariantSummary }
            }
        }
        onActivated: {
            var chosen=search.results[activatedIndex]
            var all=player.openSourcePlaylistId!==""?player.sourcePlaylistTracks:player.playlistTracks
            if(!chosen || !all)return
            for(var i=0;i<all.length;i++)if(String(all[i].id)===String(chosen.id)){player.playPlaylistTrack(i);return}
        }
    }
}
