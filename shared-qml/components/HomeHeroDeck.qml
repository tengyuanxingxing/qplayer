import QtQuick
import miuix.Core
import "."

Flickable {
    id:deck
    property var songs:[]
    property real cardWidth:Math.min(330,width-56)
    contentWidth:cardWidth*3+48;contentHeight:height;clip:true
    Repeater {
        model:3
        Rectangle {
            x:16+index*(deck.cardWidth+10);y:0;width:deck.cardWidth;height:deck.height
            radius:22;color:index===0?"#8BB9ED":(index===1?"#A8C9BF":"#D9B4CE")
            CoverImage {
                anchors.right:parent.right;anchors.rightMargin:16;y:16;width:96;height:96;radius:18
                source:deck.songs.length>index?(deck.songs[index].coverUrl||deck.songs[index].artworkUrl):""
            }
            Text { x:18;y:20;width:parent.width-146;text:index===0?"猜你喜欢":(index===1?"每日推荐":"音乐探索");wrapMode:Text.Wrap;font.pixelSize:24;font.weight:Font.DemiBold;color:"#12313E" }
            Text { x:18;anchors.bottom:parent.bottom;anchors.bottomMargin:18;width:parent.width-76;text:deck.songs.length>index?deck.songs[index].name:"发现下一首喜欢";font.pixelSize:16;color:"#173E4B";elide:Text.ElideRight }
            Icon { anchors.right:parent.right;anchors.rightMargin:20;anchors.bottom:parent.bottom;anchors.bottomMargin:16;name:"play_circle";width:36;height:36;color:"#F8FFFFFF" }
            Ripple { anchors.fill:parent;clipRadius:22;onClicked:if(tapAllowed)player.playRecommendation(Math.min(index,Math.max(0,deck.songs.length-1))) }
        }
    }
}
