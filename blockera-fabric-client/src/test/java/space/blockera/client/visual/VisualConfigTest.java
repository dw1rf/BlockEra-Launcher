package space.blockera.client.visual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VisualConfigTest {
    @Test
    void clampsCrosshairGeometryAndRepairsColors() {
        VisualConfig config = new VisualConfig(
            1, true, true, "invalid", 99, -4, 10, true, null
        );

        assertEquals("#F4F5F6", config.crosshairColor());
        assertEquals(12, config.crosshairSize());
        assertEquals(0, config.crosshairGap());
        assertEquals(3, config.crosshairThickness());
        assertEquals("#FF667A", config.hitColor());
    }

    @Test
    void colorsAreOpaqueArgbAndVisualOnly() {
        VisualConfig config = VisualConfig.defaults();

        assertEquals(0xFFF4F5F6, config.crosshairArgb());
        assertEquals(0xFFFF667A, config.hitColorArgb());
        assertTrue(config.crosshairEnabled());
        assertTrue(config.hitColorEnabled());
    }
}
