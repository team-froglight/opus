package opus.minecraft;

import com.sun.jna.Native;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PointerInfoTest {
    @Test void nativePointerLayoutPreservesContactFields() {
        var info = new WindowsTouchpad.PointerInfo();
        assertEquals(Native.POINTER_SIZE == 8 ? 96 : 88, info.size());
        info.pointerType = 5;
        info.pointerId = 23;
        info.pointerFlags = 0x8000;
        info.write();
        assertEquals(5, info.getPointer().getInt(0));
        assertEquals(23, info.getPointer().getInt(4));
        assertEquals(0x8000, info.getPointer().getInt(12));
        info.getPointer().setInt(12, 0);
        info.read();
        assertEquals(0, info.pointerFlags);
    }
}
