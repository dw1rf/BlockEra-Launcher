package space.blockera.client.hud;

public enum HudAnchor {
    TOP_LEFT(0, 0),
    TOP_CENTER(1, 0),
    TOP_RIGHT(2, 0),
    CENTER_LEFT(0, 1),
    CENTER(1, 1),
    CENTER_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2),
    BOTTOM_CENTER(1, 2),
    BOTTOM_RIGHT(2, 2);

    private final int horizontal;
    private final int vertical;

    HudAnchor(int horizontal, int vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public HudPoint resolve(int screenWidth, int screenHeight, int width, int height, int offsetX, int offsetY) {
        int x = switch (horizontal) {
            case 0 -> offsetX;
            case 1 -> (screenWidth - width) / 2 + offsetX;
            default -> screenWidth - width - offsetX;
        };
        int y = switch (vertical) {
            case 0 -> offsetY;
            case 1 -> (screenHeight - height) / 2 + offsetY;
            default -> screenHeight - height - offsetY;
        };
        return new HudPoint(x, y);
    }
}
