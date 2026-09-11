package fr.iamacat.bridge.forge;

import fr.iamacat.spi.render.GlBackend;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

/**
 * LWJGL 2 driver implementing the pure SPI GlBackend instancing contract.
 * Targets OpenGL 3.1+ instancing primitives on 1.7.10 / 1.12.2 (Compatibility Profile).
 * Zero Minecraft imports, pure LWJGL2 calls.
 */
public class Lwjgl2Backend implements GlBackend {

    @Override
    public int genBuffers() {
        return GL15.glGenBuffers();
    }

    @Override
    public void bindBuffer(int target, int buffer) {
        GL15.glBindBuffer(target, buffer);
    }

    @Override
    public void bufferData(int target, FloatBuffer data, int usage) {
        if (data == null) {
            throw new NullPointerException("E_GL_BUFFER:null");
        }
        GL15.glBufferData(target, data, usage);
    }

    @Override
    public void deleteBuffers(int buffer) {
        GL15.glDeleteBuffers(buffer);
    }

    @Override
    public int genVertexArrays() {
        return GL30.glGenVertexArrays();
    }

    @Override
    public void bindVertexArray(int array) {
        GL30.glBindVertexArray(array);
    }

    @Override
    public void deleteVertexArrays(int array) {
        GL30.glDeleteVertexArrays(array);
    }

    @Override
    public void enableVertexAttribArray(int index) {
        GL20.glEnableVertexAttribArray(index);
    }

    @Override
    public void disableVertexAttribArray(int index) {
        GL20.glDisableVertexAttribArray(index);
    }

    @Override
    public void vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset) {
        GL20.glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        GL33.glVertexAttribDivisor(index, divisor);
    }

    @Override
    public int createShader(int type) {
        return GL20.glCreateShader(type);
    }

    @Override
    public void shaderSource(int shader, String source) {
        GL20.glShaderSource(shader, source);
    }

    @Override
    public void compileShader(int shader) {
        GL20.glCompileShader(shader);
    }

    @Override
    public boolean getShaderCompileStatus(int shader) {
        return GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE;
    }

    @Override
    public String getShaderInfoLog(int shader) {
        int len = GL20.glGetShaderi(shader, GL20.GL_INFO_LOG_LENGTH);
        return GL20.glGetShaderInfoLog(shader, len > 0 ? len : 1024);
    }

    @Override
    public void deleteShader(int shader) {
        GL20.glDeleteShader(shader);
    }

    @Override
    public int createProgram() {
        return GL20.glCreateProgram();
    }

    @Override
    public void attachShader(int program, int shader) {
        GL20.glAttachShader(program, shader);
    }

    @Override
    public void linkProgram(int program) {
        GL20.glLinkProgram(program);
    }

    @Override
    public boolean getProgramLinkStatus(int program) {
        return GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_TRUE;
    }

    @Override
    public String getProgramInfoLog(int program) {
        int len = GL20.glGetProgrami(program, GL20.GL_INFO_LOG_LENGTH);
        return GL20.glGetProgramInfoLog(program, len > 0 ? len : 1024);
    }

    @Override
    public void useProgram(int program) {
        GL20.glUseProgram(program);
    }

    @Override
    public void deleteProgram(int program) {
        GL20.glDeleteProgram(program);
    }

    @Override
    public int getUniformLocation(int program, String name) {
        return GL20.glGetUniformLocation(program, name);
    }

    @Override
    public void uniformMatrix4fv(int location, boolean transpose, FloatBuffer matrices) {
        GL20.glUniformMatrix4(location, transpose, matrices);
    }

    @Override
    public void uniform1i(int location, int value) {
        GL20.glUniform1i(location, value);
    }

    @Override
    public void uniform1f(int location, float value) {
        GL20.glUniform1f(location, value);
    }

    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        GL20.glUniform4f(location, x, y, z, w);
    }

    @Override
    public void drawArraysInstanced(int mode, int first, int count, int instanceCount) {
        GL31.glDrawArraysInstanced(mode, first, count, instanceCount);
    }
}
