package net.minecraft.entity.ai.attributes;

/**
 * B3 compile stub: shape-only vanilla attribute marker used by the spawn
 * hp seam in forge/ (MatouBridgeMod lands the content hp on the beast's
 * max-health attribute, hub decisions/SPAWN.md hp tranche). Never runs
 * (compile classpath only). An interface live (measured:
 * IncompatibleClassChangeError when stubbed as a class — loud, never
 * silent), so an interface here: the landing's invokeinterface must
 * link. Class names are identical in searge and MCP, so no mapping is
 * ever needed. Drift fails loudly on the owning side.
 */
public interface IAttribute {
}
