package fr.iamacat.bridge.forge;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * Registration landing (see hub decisions/REGISTRATION.md): the one
 * generic Forge block every content block registers as. Physics rides
 * the content {@code BlockSpec} (hardness, opacity) — never hardcoded
 * per content, never a subclass per content. Only this package may
 * import {@code net.minecraft} / {@code cpw.mods}.
 */
public final class MatouBlock extends Block {
    private final boolean opaque;

    /**
     * Args are pre-validated by the registering mod (E_REG_* owns the
     * refusals); the constructor only lands them.
     */
    public MatouBlock(String shortName, float hardness, boolean opaque) {
        super(Material.rock);
        setBlockName(shortName);
        setHardness(hardness);
        this.opaque = opaque;
    }

    @Override
    public boolean isOpaqueCube() {
        return opaque;
    }
}
