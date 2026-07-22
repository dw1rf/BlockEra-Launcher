package space.blockera.client.hud.editor;

public record HudRect(int x, int y, int width, int height) {
	public HudRect {
		if (width < 0 || height < 0) {
			throw new IllegalArgumentException("HUD rectangle dimensions must be non-negative");
		}
	}

	public int left() {
		return x;
	}

	public int top() {
		return y;
	}

	public int right() {
		return x + width;
	}

	public int bottom() {
		return y + height;
	}

	public boolean contains(double pointX, double pointY) {
		return pointX >= left() && pointX < right() && pointY >= top() && pointY < bottom();
	}

	public boolean intersects(HudRect other) {
		return left() < other.right() && right() > other.left()
			&& top() < other.bottom() && bottom() > other.top();
	}
}
