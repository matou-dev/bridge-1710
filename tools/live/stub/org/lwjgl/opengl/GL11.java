package org.lwjgl.opengl;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * Shape-only compile stub for LWJGL 2 GL11.
 * Never runs (compile classpath only).
 */
public class GL11 {
    public static int GL_MODELVIEW_MATRIX;
    public static int GL_PROJECTION_MATRIX;
    public static int GL_TRUE;
    public static int GL_FALSE;
    public static int GL_TRIANGLES;
    public static int GL_FLOAT;
    public static int GL_DEPTH_TEST;
    public static int GL_CULL_FACE;
    public static int GL_BACK;
    public static int GL_BLEND;
    public static int GL_SRC_ALPHA;
    public static int GL_ONE_MINUS_SRC_ALPHA;
    // Measured on the provisioned 2.9.4-nightly-20150209 bytes (javap:
    // public static int glGetError(), GL_NO_ERROR) — the draw-proof
    // tripwire in InstancedMeshRenderer judges its own draw, never MC's.
    public static int GL_NO_ERROR;

    public static int glGetError() { return 0; }

    public static void glEnable(int cap) {}
    public static void glDisable(int cap) {}
    public static void glDepthMask(boolean flag) {}
    public static void glCullFace(int mode) {}
    public static void glBlendFunc(int sfactor, int dfactor) {}
    public static void glGetFloat(int pname, FloatBuffer params) {}

    // Core GL11 texture upload (V2 tranche): same signatures on every
    // LWJGL 2.x (glGenTextures/glBindTexture/glTexImage2D with the border
    // arg/glTexParameteri/glDeleteTextures) — the live proof links them
    // against the provisioned 2.9.4-era bytes, never this stub.
    public static int glGenTextures() { return 0; }
    public static void glBindTexture(int target, int texture) {}
    public static void glTexImage2D(int target, int level, int internalFormat,
            int width, int height, int border, int format, int type,
            ByteBuffer pixels) {}
    public static void glTexParameteri(int target, int pname, int param) {}
    public static void glDeleteTextures(int texture) {}
}
