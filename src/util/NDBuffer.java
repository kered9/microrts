package util;

import java.nio.IntBuffer;
import java.util.Arrays;

/**
 * Immitate row-level n-dimensional array layout backed
 * by a flat buffer.
 */
public class NDBuffer {

    private final IntBuffer buffer;
    private final int numDims;
    private final int[] dims;
    private final int[] offsets;

    public NDBuffer(IntBuffer buffer, int[] dims) {
        this.buffer = buffer;
        this.dims = dims;
        this.numDims = dims.length;

        this.offsets = new int[numDims];
        this.offsets[numDims-1] = 1;
        if (numDims > 1) {
            for (int i = numDims-2; i >= 0; i--) {
                this.offsets[i] = this.offsets[i+1]*dims[i+1];
            }
        }
    }

    /**
     * Set all values in tail-ordered block to 0.
     */
    public void resetSegment(int[] segment) {
        int offset = 0;
        for (int i = 0; i < segment.length; i++) {
            offset += segment[i] * offsets[i];
        }

        if (buffer.hasArray()) {
            final int bufferOffset = buffer.arrayOffset();
            Arrays.fill(buffer.array(), offset+bufferOffset, offset+bufferOffset+offsets[segment.length-1], 0);
        } else {
            for (int pos = 0; pos < offsets[segment.length-1]; pos++) {
                buffer.put(offset+pos, 0);
            }
        }
    }

    public void set(int[] point, int value) {
        this.set(point, 0, value);
    }

    public void set(int[] point, int segmentPos, int value) {
        int pos = segmentPos;
        for (int i = 0; i < point.length; i++) {
            pos += point[i] * offsets[i];
        }
        buffer.put(pos, value);
    }

    public IntBuffer getBuffer() {
        return this.buffer;
    }

}