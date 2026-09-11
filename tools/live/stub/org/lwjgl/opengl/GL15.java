package org.lwjgl.opengl;

import java.nio.FloatBuffer;

/**
 * Shape-only compile stub for LWJGL 2 GL15.
 * Never runs (compile classpath only).
 */
public class GL15 {
    public static int GL_ARRAY_BUFFER;
    public static int GL_STATIC_DRAW;
    public static int GL_DYNAMIC_DRAW;
    public static int GL_STREAM_DRAW;

    public static int glGenBuffers() {
        return 0;
    }

    public static void glBindBuffer(int target, int buffer) {}

    public static void glBufferData(int target, FloatBuffer data, int usage) {}

    public static void glDeleteBuffers(int buffer) {}
}
