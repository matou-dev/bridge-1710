package org.lwjgl.opengl;

import java.nio.FloatBuffer;

/**
 * Shape-only compile stub for LWJGL 2 GL20.
 * Never runs (compile classpath only).
 */
public class GL20 {
    public static int GL_VERTEX_SHADER;
    public static int GL_FRAGMENT_SHADER;
    public static int GL_COMPILE_STATUS;
    public static int GL_LINK_STATUS;
    public static int GL_INFO_LOG_LENGTH;

    public static void glEnableVertexAttribArray(int index) {}
    public static void glDisableVertexAttribArray(int index) {}
    public static void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {}

    public static int glCreateShader(int type) {
        return 0;
    }
    public static void glShaderSource(int shader, CharSequence string) {}
    public static void glCompileShader(int shader) {}
    public static int glGetShaderi(int shader, int pname) {
        return 0;
    }
    public static String glGetShaderInfoLog(int shader, int maxLength) {
        return "";
    }
    public static void glDeleteShader(int shader) {}

    public static int glCreateProgram() {
        return 0;
    }
    public static void glAttachShader(int program, int shader) {}
    public static void glLinkProgram(int program) {}
    public static int glGetProgrami(int program, int pname) {
        return 0;
    }
    public static String glGetProgramInfoLog(int program, int maxLength) {
        return "";
    }
    public static void glUseProgram(int program) {}
    public static void glDeleteProgram(int program) {}

    public static int glGetUniformLocation(int program, CharSequence name) {
        return 0;
    }
    public static void glUniformMatrix4(int location, boolean transpose, FloatBuffer matrices) {}
    public static void glUniform1i(int location, int value) {}
    public static void glUniform1f(int location, float value) {}
    public static void glUniform4f(int location, float x, float y, float z, float w) {}
}
