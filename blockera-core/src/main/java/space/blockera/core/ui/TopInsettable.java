package space.blockera.core.ui;

/** Implemented by vanilla viewport widgets whose upper clipping edge can honor Blockera chrome. */
public interface TopInsettable {
	void blockera$ensureTopInset(int minimumTop);
}
