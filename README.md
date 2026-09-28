# Opus

A small declarative UI library for Kotlin and Java, with a Minecraft 1.21.1 renderer.

## Modules

| Module | Responsibility | Dependencies |
| --- | --- | --- |
| `opus-core` | Components, state, composition, modifiers | Kotlin/JVM; no game or native APIs |
| `opus-yoga` | Flex and grid layout | Core and LWJGL Yoga |
| `opus-minecraft` | Screens, input, shaders, textures and fonts | Core, Yoga and Minecraft |
| `opus-neoforge` | Client shader registration and reload lifecycle | Minecraft adapter and NeoForge |
| `opus-showcase` | Interactive component gallery | All of the above |

Public names describe their role: `Composition`, `MutableState`, `UiNode`, `Theme`, `UiScreen`, `Fonts`. Packages carry the library name (`opus.core`, `opus.minecraft`, `opus.neoforge`). Rendering helpers and native font internals are not part of the public API.

## Kotlin

After client initialization, open a screen on the Minecraft client thread:

```kotlin
import net.minecraft.client.Minecraft
import opus.core.*
import opus.minecraft.UiScreen

Minecraft.getInstance().setScreen(UiScreen {
    var count by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().padding(16f)) {
        Heading("Counter")
        Text("Count: $count")
        Button("Add", onClick = { count++ })
    }
})
```

`remember` keeps values across recompositions. State changes recompose the current tree synchronously; access UI state from the UI thread. `UiScreen` owns composition, layout and font-cache cleanup. A standalone `setContent { ... }` returns an `AutoCloseable` `Composition`; close it when finished.

## Java

Use `Ui` for Java callbacks, without `Function0`, `Unit.INSTANCE`, or generated `Kt` names:

```java
import opus.core.Modifier;
import opus.core.Modifiers;
import opus.core.MutableState;
import opus.core.Ui;
import opus.minecraft.UiScreen;

UiScreen.open(() -> {
    MutableState<Integer> count = Ui.remember(() -> Ui.state(0));
    Ui.column(() -> {
        Ui.text("Count: " + count.getValue());
        Ui.button("Add", () -> count.setValue(count.getValue() + 1));
    }, Modifiers.padding(Modifier.EMPTY, 16f));
});
```

The complete Kotlin component surface is also exposed through `Components`, `Layouts`, `Modifiers`, `States` and `Compositions`. `Ui` provides convenient Java callbacks for common controls. See the compiled [Java example](opus-showcase/src/main/java/opus/showcase/CounterExample.java).

## NeoForge setup

Call `UiClient.initialize(modBus)` once from a **client-only** mod constructor, before opening screens. The adapter handles shader registration and replacement on resource reload.

```java
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import opus.neoforge.UiClient;

@Mod(value = "your_mod", dist = Dist.CLIENT)
public final class ClientMod {
    public ClientMod(IEventBus modBus) {
        UiClient.initialize(modBus);
    }
}
```

This repository uses local Gradle project dependencies; no published Maven release is assumed. `opus-showcase/build.gradle.kts` is the complete development and packaging example: it includes the library source sets for development, embeds the library jars and Kotlin runtime, and copies `assets/opus` into the consuming mod's resources. Keep the shader assets when packaging your mod.

## Animations

Use `animateFloat` for position, size, corner radius or progress, and `animateColor` for ARGB colors. They follow a changing target and return the current displayed value:

```kotlin
var expanded by remember { mutableStateOf(false) }
val size = animateFloat(if (expanded) 64f else 32f, 350, Easing.EASE_IN_OUT)
val color = animateColor(if (expanded) 0xFFC59AE8.toInt() else 0xFF5DD2BD.toInt())
Box(Modifier.size(size, size).background(color, 8f)) {}
Button("Animate", onClick = { expanded = !expanded })
```

Java uses `Ui.animateFloat(target, durationMillis, Easing.EASE_IN_OUT)` and `Ui.animateColor(target)`. Both have defaults of 220 ms and `EASE_OUT`; `LINEAR` is also available. The first composition displays the target immediately. Later target changes animate from the displayed value, including reversals during a transition. Duration and easing are selected when a new target arrives; duration zero snaps immediately. Keep animation calls in stable composition slots, just like `remember`.

`UiScreen` advances animations before layout on each rendered frame. Switches animate automatically; the gallery's **Styling → Motion studio** demonstrates movement, size, shape, color and progress. `screen.setReducedMotion(true)` snaps active and future transitions to their targets. The gallery exposes this option as **Reduce motion**.

Other hosts call `composition.advanceAnimations(monotonicTimeNanos)` before layout/drawing, beginning with an initial frame to establish the clock. `composition.animationDurationScale` controls speed (1 = normal, 2 = half speed, 0 = reduced motion). Tracks leaving the composition stop; closing the composition releases them. Frame updates batch into at most one recomposition, and completed animations stop requesting updates. This version recomposes the whole tree while values change, so avoid animating thousands of nodes simultaneously.

### Views and visibility

```kotlin
AnimatedContent(selectedPage, transition = ViewTransition.SLIDE_HORIZONTAL) { page ->
    when (page) {
        "home" -> HomeView()
        else -> DetailsView()
    }
}
AnimatedVisibility(showDetails) {
    Text("Extra details")
}
```

`AnimatedContent` fades the outgoing view out before bringing the latest requested view in. Choose `FADE`, `SLIDE_HORIZONTAL`, or `SLIDE_VERTICAL`; slides include a fade. The default total duration is 280 ms. Rapid navigation coalesces to the latest target, and changing your mind during a transition reverses from the displayed position. Content starts at full visibility on initial composition. Hoist state above the container when it should survive navigation; remembered values inside a removed view are released.

`AnimatedVisibility` keeps content composed during its 220 ms exit, then releases its layout space and remembered state. It reserves the content's normal layout size during the fade; it does not automatically animate intrinsic height. Always call it, including when `visible` is false. Outgoing and partially entered content cannot receive input. Both APIs respect reduced motion.

Java equivalents are `Ui.animatedContent(page, value -> renderPage(value))` and `Ui.animatedVisibility(showDetails, () -> renderDetails())`, with overloads for modifier, transition and duration.

### Ordinary style transitions

```kotlin
Box(
    Modifier.size(if (expanded) 180f else 100f, if (expanded) 60f else 40f)
        .background(if (expanded) purple else teal, if (expanded) 18f else 5f)
        .padding(if (expanded) 14f else 8f)
        .animateChanges(durationMillis = 300)
) { Text("Restyle me") }
```

`animateChanges` animates preceding size, padding, offset, opacity, background, border, outline, shadow and corner values together. Java uses `Ui.transition(modifier, durationMillis, easing)`. Place it after the properties to animate and keep the call in a stable composition slot. New/removed properties, automatic dimensions and unsupported layout properties snap. For example, use fixed size transitions when you want surrounding layout to move smoothly.

Offsets and opacity apply to descendants; hit testing uses those same coordinates. Buttons animate accent/disabled colors and hover feedback automatically. The gallery's **Styling → Transitions** demonstrates view changes, visibility and style transitions; the main gallery tabs also transition.

Dropdowns animate their inline menu height over 180 ms, moving subsequent content with it. The trigger and menu have separate surfaces; shader-drawn arrows and checkmarks stay smooth at different GUI scales. Options become selectable after opening finishes, and closing rows cannot receive clicks. Clicking outside or pressing Escape dismisses an open menu; the dismissing click is consumed. Reduced motion makes opening and closing immediate. Leave the dropdown height automatic so its menu has room to expand.

## Input components

`TextField`, `TextArea`, `NumberField`, and `DateField` are controlled components: keep the value in state and update it in `onValueChange`. Their labels stay visible above the editor. `InputOptions` supplies placeholder, helper text, error text, enabled/read-only state, maximum length, and accent color. Error text replaces helper text and wraps within the field width.

```kotlin
var name by remember { mutableStateOf("") }
TextField(name, { name = it }, "Display name", Modifier.fillMaxWidth(),
    InputOptions(placeholder = "Your name", maxLength = 40,
        helperText = "Up to 40 characters."), onSubmit = { save(name) })

var notes by remember { mutableStateOf("") }
TextArea(notes, { notes = it }, "Notes", Modifier.fillMaxWidth(), rows = 4)
```

Java uses `Ui.textField(value, onChange, label)`, `Ui.textArea(...)`, `Ui.numberField(...)`, and `Ui.dateField(...)`. Optional overloads accept a modifier and `InputOptions.DEFAULT.withPlaceholder("Your name").withMaxLength(40)`; text and number fields also accept a `Runnable` submit action. Text areas accept a final row count. Place them in a full-width container or give them an explicit width.

`NumberField` keeps a **string draft**, allowing empty text, `-`, and trailing decimal points during editing. It accepts decimal digits, an optional leading minus, and one dot. `NumberInput.parseOrNull(draft)` returns a finite number only when the draft is complete. Validate required values and ranges in your application and pass an explanatory `error`; the component does not silently replace incomplete values with zero.

`DateField` accepts an ISO `yyyy-MM-dd` string draft. Use `DateInput.parseOrNull(draft)` to validate a complete date, including leap days. Its calendar button opens an inline month view; Alt+Down opens it from the keyboard. Arrows move between days, PageUp/PageDown change months, Home/End choose a month's first/last day, and Enter selects. Escape closes the picker. Calendar selection emits the same ISO string as typing.

The Minecraft adapter uses Minecraft's text editing models for caret movement, selection and clipboard shortcuts, with rounded field surfaces and visible focus borders. It corrects multiline selection replacement at character limits and adds drag selection for single-line fields and up to 100 undo/redo steps (`Ctrl+Z`, `Ctrl+Y` or `Ctrl+Shift+Z`). Editor state follows the node's stable identity across recompositions; removing the node releases it. Programmatic value changes reset undo history. Read-only fields allow selection/copy; disabled fields skip focus. Text editing uses the Minecraft font, independently of custom fonts used by `Text`.

Maximum lengths count UTF-16 units, without splitting surrogate pairs. Over-limit programmatic values are clipped in the emitted node, including when the limit is reduced; this does not mutate application state until the next accepted edit. Multiline values normalize line endings, and single-line fields remove line breaks. Selection replacement uses the space released by the selection, even at the limit.

Number fields include increment/decrement arrows and support keyboard Up/Down. The optional final `NumberOptions(step = 1.0, min = null, max = null)` argument configures their step and bounds; Java can use `new NumberOptions(0.5, 0.0, 100.0)`. Stepping uses decimal arithmetic, clamps to the configured limits, and starts incomplete drafts from zero. Arrows are inactive at a bound or when the result would exceed `maxLength`. Typing remains available for drafts and application validation. Read-only and disabled fields cannot step.

Read-only fields keep selection and copying available without displaying an editing caret, including in multiline fields.

### Keyboard controls

| Key | Behavior |
| --- | --- |
| Tab / Shift+Tab | Next / previous enabled control; scroll it into view |
| Enter / Space | Activate buttons, toggles and disclosure controls |
| Arrows | Adjust sliders; move through choices or pages |
| Shift+Arrow on a slider | Adjust by 10% instead of 1% |
| Home / End | First / last choice or slider/page boundary |
| Enter / Space in an open dropdown | Commit the highlighted choice |
| Space in multiple choice | Toggle the focused option |
| Enter in a text/number field | Submit, when a callback is supplied |
| Enter in a text area | Insert a newline |
| Alt+Down in a date field | Open the calendar picker |
| Escape | Dismiss an open dropdown/calendar, then clear focus, then close the screen |

Focus skips hidden controls, collapsed content, disabled inputs and outgoing transitions. Use `Modifier.inputEnabled(false)` to block an existing control or subtree. A focused text editor blocks swipe navigation, and multiline scrolling passes back to the screen at the editor's boundaries. The **Controls → Text inputs** gallery section demonstrates editable, validated, multiline, read-only and disabled fields.

## Touchpad gestures

`UiScreen` preserves fractional two-finger vertical scrolling and the momentum supplied by the operating system. Scroll positions clamp at the content edges immediately, so reversing direction responds without an overscroll delay. It does not add a second layer of inertia.

Horizontal navigation is opt-in:

```java
screen.setOnSwipe(direction -> {
    if (direction == SwipeDirection.LEFT) showNextPage();
    else showPreviousPage();
    screen.scrollToTop();
});
```

The gallery enables this for its four tabs, stopping at the first and last tab. A transient arrow pulls in from the left edge for previous navigation or the right edge for next navigation. It follows swipe progress, highlights a completed navigation, then retreats and fades. Unavailable directions show a muted hint. These overlays reserve no layout space. Override `UiScreen.isSwipeAvailable(direction)` to report your own navigation boundaries; returning false prevents the callback. Reduced motion keeps the hint stationary and hides it without a fade.

A swipe needs a full pull of eight horizontal scroll units. The edge arrow follows that pull over 64 GUI pixels and changes color at full extension. Full extension only arms navigation: releasing commits it, while a shorter pull or pulling back below the threshold cancels it. Overshoot is clamped so pulling back responds immediately. Continuous input never repeatedly switches tabs. Diagonal gestures favor vertical scrolling; slider dragging and open dropdowns/popups block navigation. `setOnSwipe(null)` disables horizontal navigation. View transitions continue to respect reduced motion.

Use `ArrowButton(direction, label, onClick)` or Java's `Ui.arrowButton(direction, label, runnable)` for explicit arrow buttons in your own navigation. `ArrowDirection.LEFT` and `RIGHT` describe the visible arrow; the callback decides what it does.

`UiScreen` observes native Windows touchpad contact messages and commits only when the last tracked finger lifts. Holding still keeps the pull armed; subsequent scroll momentum cannot navigate again. Focus loss, pointer cancellation and screen removal cancel the pull. Partial pulls retreat immediately on release. Windows still processes the messages normally, preserving its scroll direction and sensitivity settings.

This requires a Windows version providing [RegisterTouchpadCapableThread](https://learn.microsoft.com/en-us/windows/win32/input-precisiontouchpad/registertouchpadcapable) and a compatible touchpad. The API is resolved at runtime. Other systems, or a failed registration, retain ordinary scrolling and tab buttons without timer-based swipe navigation. Query `UiScreen.isTouchpadNavigationSupported()` after initialization to check native registration; it does not detect whether a touchpad is connected.

Other hosts can use the platform-independent `ScrollGestures` recognizer directly. Feed fractional deltas to `accept`, process `verticalScroll`, and call `release` on native finger-up to receive the optional `swipe`. By default, neither a long pause nor `advance` ends a gesture. Scroll-only hosts can explicitly opt into `inferRelease = true` and call `advance(monotonicTimeNanos)` each frame, but that approximation cannot distinguish stationary fingers from release. `UiScreen` never uses it. Directions follow the signed horizontal delta (negative = `LEFT`), after the OS's scroll-direction preference; invert the callback mapping if your host uses the opposite convention. Minecraft's **Discrete Scrolling** option quantizes input before it reaches the screen; leave it off for precision scrolling. Pinch, rotation and three-finger OS gestures are not mapped to navigation.

## Fonts

```java
String bodyFont = Fonts.register("body", pathToLicensedTtf);
Fonts.system();     // Cached system and per-user font discovery
Fonts.files();      // Drop-in and explicitly registered fonts
Fonts.available(); // Includes Minecraft's built-in fonts
```

Pass the returned ID as `Text` / `Ui.text`'s `fontId`. `Fonts.setDirectory(path)` changes the drop-in directory; its default is `<game directory>/config/opus/fonts`. `Fonts.refresh()` rescans directories and releases the previous atlas cache. Font registration, refresh and rendering run on the client thread.

Discovery supports `.ttf` files in Windows, macOS and Linux font directories, including per-user directories. Register the same font file on each installation when you need consistent typography; available system fonts vary between computers. Glyphs load on demand, use shader antialiasing, and share kerning-aware metrics between layout and drawing. NFC normalization handles decomposed accents. Complex-script shaping, bidirectional text and color emoji are not implemented. Text components currently render one line.

## Portability

- Core compositions can be used without Minecraft or NeoForge. A new host needs layout integration, a renderer, input dispatch and text measurement.
- The Minecraft module has no NeoForge imports. `ShaderPrograms.register` is the lifecycle hook for another Minecraft loader adapter. A Fabric adapter is not included, and mappings/version changes still require integration work.
- Development and test native dependencies are selected from the host OS and architecture. The Yoga jar is configured to bundle native resources for Windows, Linux and macOS on x64 and ARM64. Minecraft supplies its own LWJGL core/STB runtime; Yoga is supplied by this library.
- The current toolchain is Java 21. This is a JVM library, not Kotlin Multiplatform or a browser UI framework.
- Font-directory selection and discovery have automated tests. Running tests on one OS does not establish that the renderer works on every OS/GPU.

## Build and try

```text
./gradlew :opus-core:test :opus-yoga:test :opus-minecraft:test :opus-showcase:build
./gradlew :opus-showcase:runClient
```

On Windows use `gradlew.bat`. Open **Opus Gallery** from Minecraft's title screen. The gallery has Controls, Typography, Styling and Layout tabs.

If Windows Java fails before the build with `Unable to establish loopback connection` and an `UnixDomainSockets.connect` stack trace, use a writable project directory for temporary sockets in that terminal session:

```powershell
$socketDirectory = (New-Item -ItemType Directory -Force build/sockets).FullName
$env:JAVA_TOOL_OPTIONS += ' "-Djdk.net.unixdomain.tmpdir=' + $socketDirectory + '"'
.\gradlew.bat :opus-core:test :opus-yoga:test :opus-minecraft:test :opus-showcase:build
```

## API migration

This is a source-breaking pre-release cleanup. Update imports and rebuild consumers; old binary names are not retained. See [MIGRATION.md](MIGRATION.md).
