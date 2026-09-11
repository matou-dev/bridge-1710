package fr.iamacat.bridge.forge;

/**
 * Tranche-1 parity shell (see hub decisions/GL_INSTANCING_ADAPTER.md):
 * same basename as bridge-1122's Lwjgl2Backend so hub
 * tools/check-bridges.sh holds its forge file-set.
 */
public final class Lwjgl2Backend {
    private Lwjgl2Backend() {
        throw new AssertionError("E_GL_SHELL:unwired parity shell");
    }
}
