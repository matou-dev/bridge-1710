package fr.iamacat.bridge.forge;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * Registration landing (see hub decisions/REGISTRATION.md): the one
 * generic Forge block every content block registers as. Physics lands
 * in the vanilla slots (hardness via the setter, opacity in the
 * vanilla {@code opaque} slot the stock {@code isOpaqueCube} reads) —
 * never hardcoded per content, never a subclass per content, never a
 * shadow field (a same-named project field would hide the vanilla one
 * and split readers — measured in reobf output, refused by shape).
 * Only this package may import {@code net.minecraft} /
 * {@code cpw.mods}.
 */
public final class MatouBlock extends Block {
    /**
     * Args are pre-validated by the registering mod (E_REG_* owns the
     * refusals); the constructor only lands them.
     */
    public MatouBlock(String shortName, float hardness, boolean opaque) {
        super(Material.rock);
        setBlockName(shortName);
        setHardness(hardness);
        super.opaque = opaque;
    }

    @Override
    public boolean isOpaqueCube() {
        return super.opaque;
    }
}
