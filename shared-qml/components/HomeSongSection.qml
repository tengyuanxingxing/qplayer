import QtQuick
import miuix.Core
import "."

Item {
    id: section
    objectName: "homeSongSection"
    property string title: ""
    property string subtitle: ""
    property var songs: []
    property string kind: "home"
    property bool pageActive: true
    height: songs.length > 0 ? 72 + Math.min(3, songs.length) * 76 : 92
    function play(index) {
        if (section.kind === "heart") player.playHeartExplore(index)
        else if (section.kind === "related") player.playPlaybackRelated(index)
        else player.playRecommendation(index)
    }
    Text {
        x:20;y:4;width:parent.width-112;text:section.title
        font.pixelSize:21;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor;elide:Text.ElideRight
    }
    IconButton { anchors.right:parent.right;anchors.rightMargin:20;y:0;icon:"play_arrow";enabled:section.songs.length>0;onClicked:section.play(0) }
    Text { x:20;y:36;width:parent.width-40;text:section.subtitle;font.pixelSize:12;color:Theme.color.onSurfaceVariantSummary;elide:Text.ElideRight }
    Text { x:20;y:62;width:parent.width-40;visible:section.songs.length===0;text:"暂未返回歌曲，刷新后重试";font.pixelSize:13;color:Theme.color.onSurfaceVariantSummary }
    Flickable {
        id:horizontal
        x:0;y:60;width:parent.width;height:Math.min(3,section.songs.length)*76
        clip:true;contentHeight:height
        property real columnWidth:Math.min(420,width-26)
        contentWidth:Math.max(width,Math.ceil(section.songs.length/3)*columnWidth+12)
        Repeater {
            model:section.pageActive?section.songs:null
            windowStart:Math.max(0,Math.floor(horizontal.contentX/horizontal.columnWidth)*3-3)
            windowCount:9
            SongRow {
                x:12+Math.floor(index/3)*horizontal.columnWidth;y:(index%3)*76
                width:horizontal.columnWidth-8;height:72
                rowTitle:modelData.name;rowArtist:modelData.artist
                coverThumbPath:modelData.coverThumbPath||modelData.coverUrl
                song:modelData;showLikeButton:true
                likeable:player.isMediaLikeable(modelData.id)
                liked:{var revision=player.likedRevision;return player.isMediaLiked(modelData.id)}
                onActivated:section.play(index)
                onLikeToggled:player.toggleLikeMedia(modelData.id)
            }
        }
    }
}
