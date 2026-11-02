package dev.t1m3.qplayer.desktop.window;

import dev.t1m3.qplayer.desktop.resources.ClasspathResourceLoader;
import io.github.humbleui.skija.*;
import io.github.timer_err.qml4j.engine.QmlEngine;
import io.github.timer_err.qml4j.engine.binding.DirtyQueue;
import io.github.timer_err.qml4j.engine.binding.Property;
import io.github.timer_err.qml4j.render.QmlView;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/** Production avatar/cover decoding, replacement and recorded-image GL reattachment. */
public class AccountAvatarGpuTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    public static final class PixelRatio {
        public final Property<Double> pixelRatio = new Property<>(1.0);
    }

    @Test
    public void imagePixelsSurviveReplacementAndThreeGlContexts() throws Exception {
        Assume.assumeTrue("GLFW", GLFW.glfwInit());
        Path first = temporary.newFile("red.png").toPath();
        Path second = temporary.newFile("blue.png").toPath();
        writeImage(first, 1024, 768, 0xFFCC2244);
        writeImage(second, 512, 1024, 0xFF2266DD);
        ClasspathResourceLoader resources = new ClasspathResourceLoader();
        QmlView view = QmlView.withStockTypes(new QmlEngine()).resources(resources)
                .context("player", new PixelRatio());
        view.renderer().setPictureCache(true);
        view.iconTypeface(resources.load("fonts/MaterialSymbolsRounded.ttf"));
        long window = MemoryUtil.NULL;
        try {
            view.load("import QtQuick\nimport \"components\"\nRectangle {\n"
                    + "width: 180; height: 96; color: \"#ffffff\"\n"
                    + "property string imageSource: \"" + first.toString().replace('\\', '/') + "\"\n"
                    + "AvatarImage { width:72; height:72; x:4; y:12; source:parent.imageSource }\n"
                    + "CoverImage { width:72; height:72; x:92; y:12; source:parent.imageSource }\n}");
            for (int cycle = 0; cycle < 3; cycle++) {
                GLFW.glfwDefaultWindowHints();
                GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
                window = GLFW.glfwCreateWindow(180, 96, "QPlayer avatar review", MemoryUtil.NULL, MemoryUtil.NULL);
                Assume.assumeTrue("GL window", window != MemoryUtil.NULL);
                GLFW.glfwMakeContextCurrent(window);
                GL.createCapabilities();
                try (DirectContext context = DirectContext.makeGL();
                     BackendRenderTarget target = BackendRenderTarget.makeGL(180, 96, 0, 8, 0, FramebufferFormat.GR_GL_RGBA8);
                     Surface surface = Surface.wrapBackendRenderTarget(context, target, SurfaceOrigin.BOTTOM_LEFT,
                             ColorType.RGBA_8888, ColorSpace.getSRGB())) {
                    assertNotNull(surface);
                    view.renderer().setGpuContext(context);
                    drawUntilReady(view, surface, context, cycle == 0 ? 0xFFCC2244 : 0xFF2266DD);
                    if (cycle == 0) {
                        DirtyQueue queue = view.dirtyQueue();
                        queue.install();
                        try { source(view).set(second.toString().replace('\\', '/')); queue.flush(); }
                        finally { queue.uninstall(); }
                        drawUntilReady(view, surface, context, 0xFF2266DD);
                    }
                    for (int frame = 0; frame < 20; frame++) draw(view, surface, context);
                    assertTrue("stable production images should exercise picture caching",
                            view.renderer().pictureRecordsTotal() > 0);
                    assertPixels(surface, context, 0xFF2266DD);
                    String review = System.getProperty("qplayer.review.dir", "");
                    if (!review.isEmpty()) {
                        Path directory = Path.of(review);
                        Files.createDirectories(directory);
                        try (Image image = surface.makeImageSnapshot(); Bitmap bitmap = new Bitmap()) {
                            bitmap.allocPixels(ImageInfo.makeN32Premul(180, 96));
                            assertTrue(image.readPixels(context, bitmap, 0, 0, false));
                            try (Image raster = Image.makeFromBitmap(bitmap); Data png = raster.encodeToData()) {
                                assertNotNull(png);
                                Files.write(directory.resolve("account-avatar-context-" + cycle + ".png"), png.getBytes());
                            }
                        }
                    }
                    // The Android host now retires recordings while the old GL
                    // context is current; raster decoded image data remains reusable.
                    context.flushAndSubmit(true);
                    view.renderer().setPictureCache(false);
                    view.renderer().setGpuContext(null);
                    view.renderer().setPictureCache(true);
                }
                GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
                GL.setCapabilities(null);
                GLFW.glfwDestroyWindow(window);
                window = MemoryUtil.NULL;
            }
        } finally {
            view.dispose();
            GLFW.glfwMakeContextCurrent(MemoryUtil.NULL);
            GL.setCapabilities(null);
            if (window != MemoryUtil.NULL) GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    @SuppressWarnings("unchecked")
    private static Property<String> source(QmlView view) throws Exception {
        return (Property<String>) view.root().getClass().getField("imageSource").get(view.root());
    }

    private static void drawUntilReady(QmlView view, Surface surface, DirectContext context, int expected) throws Exception {
        io.github.timer_err.qml4j.render.items.core.Image avatar =
                (io.github.timer_err.qml4j.render.items.core.Image) view.findByObjectName("accountAvatarImage");
        io.github.timer_err.qml4j.render.items.core.Image cover =
                (io.github.timer_err.qml4j.render.items.core.Image) view.findByObjectName("coverArtImage");
        long end = System.nanoTime() + 3_000_000_000L;
        do {
            draw(view, surface, context);
            if (avatar.status.peekInt() == 1 && avatar.loadedSource.equals(source(view).peek())
                    && cover.status.peekInt() == 1 && cover.loadedSource.equals(source(view).peek())) {
                draw(view, surface, context); // flush the status-dependent placeholder binding
                assertFalse(view.findByObjectName("accountAvatarPlaceholder").visible.peek());
                assertTrue(avatar.sourceSize.width.peekInt() > 0);
                assertPixels(surface, context, expected);
                return;
            }
            Thread.sleep(5);
        } while (System.nanoTime() < end);
        fail("production avatar decode did not become ready");
    }

    private static void draw(QmlView view, Surface surface, DirectContext context) {
        DirtyQueue queue = view.dirtyQueue();
        queue.install();
        try {
            queue.flush();
            view.renderer().render(surface.getCanvas(), view.root());
            queue.flush();
            context.flushAndSubmit(false);
        } finally { queue.uninstall(); }
    }

    private static void assertPixels(Surface surface, DirectContext context, int expected) {
        context.flushAndSubmit(true);
        try (Image image = surface.makeImageSnapshot(); Bitmap bitmap = new Bitmap()) {
            bitmap.allocPixels(ImageInfo.makeN32Premul(180, 96));
            assertTrue(image.readPixels(context, bitmap, 0, 0, false));
            assertEquals("avatar center", expected, bitmap.getColor(40, 48));
            assertEquals("mini cover center", expected, bitmap.getColor(128, 48));
            assertEquals("avatar must stay within its bounds", 0xFFFFFFFF, bitmap.getColor(1, 48));
        }
    }

    private static void writeImage(Path file, int width, int height, int color) throws Exception {
        try (Surface surface = Surface.makeRasterN32Premul(width, height)) {
            surface.getCanvas().clear(color);
            try (Image image = surface.makeImageSnapshot(); Data data = image.encodeToData()) {
                Files.write(file, data.getBytes());
            }
        }
    }
}
