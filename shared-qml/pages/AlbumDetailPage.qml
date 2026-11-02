import QtQuick
import QtQuick.Layouts
import miuix.Core
import "."
import "../components"

Rectangle {
    id:page
    objectName:"albumDetailPage"
    property real bottomContentInset:0
    signal back()
    signal home()
    color:Theme.color.surface
    property bool loadingWatch:player.albumLoading
    onLoadingWatchChanged:if(player.albumLoading)tracks.contentY=0
    MouseArea { anchors.fill:parent }
    ColumnLayout {
        anchors.fill:parent;spacing:0
        RowLayout {
            Layout.fillWidth:true;Layout.preferredHeight:60;spacing:4
            PageHeaderButtons { onHome:page.home();onBack:page.back() }
            Text { Layout.fillWidth:true;text:player.albumName;font.pixelSize:20;color:Theme.color.onSurfaceColor;elide:Text.ElideRight }
            Item { Layout.preferredWidth:16 }
        }
        Item {
            Layout.fillWidth:true;Layout.fillHeight:true
            VirtualSongList {
                id:tracks
                objectName:"albumDetailTracks"
                anchors.fill:parent
                bottomContentInset:page.bottomContentInset
                visible:!player.albumLoading
                list:page.visible?(player.openSourceAlbumId!==""?player.sourceAlbumTracks:player.albumTracks):null
                onActivated:player.playAlbumTrack(tracks.activatedIndex)
                headerHeight:player.albumLoading?0:238
                header:Component {
                    Item {
                        objectName:"albumScrollableHero"
                        width:tracks.width;height:238
                        CoverImage { id:cover;x:20;y:10;width:124;height:124;radius:12;icon:"album";fadeIn:false;source:player.albumCoverPath }
                        Text { x:160;y:15;width:parent.width-180;text:player.albumName;wrapMode:Text.Wrap;maximumLineCount:2;elide:Text.ElideRight;font.pixelSize:20;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor }
                        Text { x:160;y:76;width:parent.width-180;text:player.albumArtistName;font.pixelSize:14;color:Theme.color.onSurfaceVariantSummary;elide:Text.ElideRight
                            MouseArea { anchors.fill:parent;enabled:player.albumArtistMediaId!=="" || player.albumArtistId!==0;onClicked:player.openMediaArtist(player.albumArtistMediaId!==""?player.albumArtistMediaId:(""+player.albumArtistId)) }
                        }
                        Text { x:160;y:106;width:parent.width-180;text:player.albumPublishYear;font.pixelSize:12;color:Theme.color.onSurfaceVariantSummary;elide:Text.ElideRight }
                        Button { objectName:"albumFavoriteAction";x:20;y:154;width:(parent.width-52)/2;text:player.albumFavoriteBusy?"处理中…":(player.albumFavorite?"已收藏":"收藏专辑");icon:player.albumFavorite?"favorite":"favorite_border";enabled:player.albumFavoriteSupported && !player.albumFavoriteBusy;onClicked:player.toggleAlbumFavorite() }
                        Button { objectName:"albumPlayAllAction";x:(parent.width+12)/2;y:154;width:(parent.width-52)/2;text:"全部播放";icon:"play_arrow";enabled:tracks.list && tracks.list.length>0;onClicked:player.playAlbumTrack(0) }
                        Text { x:20;y:214;text:"歌曲 · "+(tracks.list?tracks.list.length:0);font.pixelSize:14;font.weight:Font.DemiBold;color:Theme.color.onSurfaceColor }
                    }
                }
            }
            LoadingIndicator { objectName:"detailLoadingIndicator";anchors.centerIn:parent;visible:player.albumLoading;running:visible }
        }
    }
}
