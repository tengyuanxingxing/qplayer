package dev.t1m3.qplayer.qml;

import io.github.timer_err.qml4j.compiler.TypeRegistry;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.render.QmlView;
import io.github.timer_err.qml4j.render.StockTypes;

/** One type catalog shared by production AOT compilation and both platform hosts. */
public final class QPlayerQmlTypes {
    private QPlayerQmlTypes() { }
    public static TypeRegistry registry() {
        return StockTypes.registry().register("PageFlickable", PageFlickable.class);
    }
    public static QmlView newView(QmlEngine engine) { return new QmlView(engine, registry()); }
}
