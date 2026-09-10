package net.minecraft.entity.ai.attributes;

/**
 * B3 compile stub: shape-only vanilla attribute instance used by the
 * spawn hp seam in forge/ (MatouBridgeMod lands the content hp through
 * {@code setBaseValue}, hub decisions/SPAWN.md hp tranche). Never runs
 * (compile classpath only). An interface live (measured:
 * IncompatibleClassChangeError when stubbed as a class — loud, never
 * silent), so an interface here: the landing's invokeinterface must
 * link. Pinned to the 1.7.10 SRG by tools/run-live.sh; drift fails
 * loudly on the owning side.
 */
public interface IAttributeInstance {
    void setBaseValue(double value);

    double getBaseValue();
}
