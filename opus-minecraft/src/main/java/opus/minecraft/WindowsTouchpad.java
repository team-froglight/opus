package opus.minecraft;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.platform.win32.BaseTSD.DWORD_PTR;
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.win32.StdCallLibrary;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Windows touchpad contact phases. Default window processing still supplies OS-configured scroll deltas. */
final class WindowsTouchpad implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(WindowsTouchpad.class);
    private static final int DOWN = 0x246, UP = 0x247, UPDATE = 0x245, CAPTURE_CHANGED = 0x24C;
    private static final int PT_TOUCHPAD = 5, CANCELLED = 0x8000;
    private static final ULONG_PTR SUBCLASS_ID = new ULONG_PTR(0x4F505553L);
    // A native callback must remain strongly reachable until its subclass has been removed.
    private static final Set<WindowsTouchpad> LIVE = Collections.newSetFromMap(new IdentityHashMap<>());

    interface Listener {
        void begin();
        void release();
        void cancel();
    }

    public interface SubclassProc extends StdCallLibrary.StdCallCallback {
        LRESULT callback(HWND window, int message, WPARAM wParam, LPARAM lParam, ULONG_PTR id, DWORD_PTR data);
    }

    public interface Controls extends StdCallLibrary {
        boolean SetWindowSubclass(HWND window, SubclassProc callback, ULONG_PTR id, DWORD_PTR data);
        boolean RemoveWindowSubclass(HWND window, SubclassProc callback, ULONG_PTR id);
        LRESULT DefSubclassProc(HWND window, int message, WPARAM wParam, LPARAM lParam);
    }

    public interface Pointers extends StdCallLibrary {
        boolean GetPointerInfo(int id, PointerInfo info);
    }

    @Structure.FieldOrder({"pointerType", "pointerId", "frameId", "pointerFlags", "sourceDevice", "hwndTarget",
        "pixel", "himetric", "pixelRaw", "himetricRaw", "time", "historyCount", "inputData", "keyStates", "performanceCount", "buttonChange"})
    public static class PointerInfo extends Structure {
        public int pointerType, pointerId, frameId, pointerFlags;
        public Pointer sourceDevice, hwndTarget;
        public POINT pixel = new POINT(), himetric = new POINT(), pixelRaw = new POINT(), himetricRaw = new POINT();
        public int time, historyCount, inputData, keyStates;
        public long performanceCount;
        public int buttonChange;
    }

    private final HWND window;
    private final Controls controls;
    private final Pointers pointers;
    private final Function registerThread;
    private final Listener listener;
    private final TouchpadContacts contacts = new TouchpadContacts();
    private final SubclassProc callback = this::message;
    private boolean installed, registered, closed;

    private WindowsTouchpad(long handle, Listener listener) {
        this.window = new HWND(Pointer.createConstant(handle));
        this.listener = listener;
        controls = Native.load("comctl32", Controls.class);
        pointers = Native.load("user32", Pointers.class);
        registerThread = registrationFunction();
    }

    /** Names are preferred; the current Windows SDK also documents ordinal 2688. */
    static Function registrationFunction() {
        NativeLibrary library = NativeLibrary.getInstance("user32");
        try { return library.getFunction("RegisterTouchpadCapableThread", Function.ALT_CONVENTION); }
        catch (UnsatisfiedLinkError missingName) {
            Pointer address = Kernel32.INSTANCE.GetProcAddress(Kernel32.INSTANCE.GetModuleHandle("user32.dll"), 2688);
            if (address == null) throw new UnsupportedOperationException("Native touchpad contact API is unavailable");
            return Function.getFunction(address, Function.ALT_CONVENTION);
        }
    }

    static WindowsTouchpad install(long glfwWindow, Listener listener) {
        if (!com.sun.jna.Platform.isWindows()) return null;
        WindowsTouchpad bridge = null;
        try {
            bridge = new WindowsTouchpad(GLFWNativeWin32.glfwGetWin32Window(glfwWindow), listener);
            if (!bridge.controls.SetWindowSubclass(bridge.window, bridge.callback, SUBCLASS_ID, new DWORD_PTR(0)))
                throw new IllegalStateException("Could not observe touchpad window messages");
            bridge.installed = true;
            LIVE.add(bridge);
            // Thread registration is reference-counted, so another library's registration is preserved.
            if (bridge.registerThread.invokeInt(new Object[]{1}) == 0)
                throw new IllegalStateException("Touchpad contact registration was declined by Windows");
            bridge.registered = true;
            LOG.info("Native touchpad finger-release tracking enabled");
            return bridge;
        } catch (RuntimeException | LinkageError error) {
            if (bridge != null) bridge.close();
            LOG.warn("Touchpad swipe navigation unavailable: {}. Tab buttons remain available.", error.toString());
            return null;
        }
    }

    boolean touching() { return !closed && contacts.touching(); }

    private LRESULT message(HWND hwnd, int message, WPARAM wParam, LPARAM lParam, ULONG_PTR id, DWORD_PTR data) {
        int pointerId = wParam.intValue() & 0xFFFF;
        boolean release = false;
        try {
            if (!closed) {
                if (message == 0x0008 || message == 0x001F || message == 0x0082
                    || (message == CAPTURE_CHANGED && contacts.contains(pointerId))) {
                    cancel();
                } else if (message == DOWN || message == UP || message == UPDATE) {
                    PointerInfo info = new PointerInfo();
                    if (pointers.GetPointerInfo(pointerId, info) && info.pointerType == PT_TOUCHPAD) {
                        if ((info.pointerFlags & CANCELLED) != 0) cancel();
                        else if (message == DOWN && contacts.down(pointerId)) listener.begin();
                        else if (message == UP) release = contacts.contains(pointerId);
                    } else if (contacts.contains(pointerId)) cancel();
                }
            }
        } catch (RuntimeException | LinkageError error) {
            LOG.warn("Touchpad contact processing cancelled", error);
            cancel();
        }
        // Forward every message. Windows retains its scrolling direction, sensitivity and gesture settings.
        LRESULT result = controls.DefSubclassProc(hwnd, message, wParam, lParam);
        // Let default processing deliver any final scroll delta before completing the gesture.
        if (!closed && release && contacts.up(pointerId)) {
            try { listener.release(); }
            catch (RuntimeException | LinkageError error) {
                LOG.warn("Touchpad release handler failed", error);
                cancel();
            }
        }
        if (message == 0x0082) close();
        return result;
    }

    private void cancel() {
        contacts.cancel();
        try { listener.cancel(); }
        catch (RuntimeException | LinkageError error) { LOG.warn("Touchpad cancellation handler failed", error); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        contacts.cancel();
        if (registered) {
            try {
                if (registerThread.invokeInt(new Object[]{0}) == 0)
                    LOG.warn("Windows declined touchpad thread unregistration");
            } catch (RuntimeException | LinkageError error) { LOG.warn("Touchpad thread cleanup failed", error); }
            registered = false;
        }
        try {
            if (!installed || controls.RemoveWindowSubclass(window, callback, SUBCLASS_ID)) {
                installed = false;
                LIVE.remove(this);
            } else LOG.warn("Touchpad observer remains attached but disabled until window destruction");
        } catch (RuntimeException | LinkageError error) { LOG.warn("Touchpad observer cleanup failed", error); }
    }
}
