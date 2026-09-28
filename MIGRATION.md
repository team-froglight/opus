# API naming migration

The library name belongs in package and artifact names. Classes now describe their responsibilities.

| Previous | Current |
| --- | --- |
| `opus.core.OpusComposition` | `opus.core.Composition` |
| `opus.core.OpusState` | `opus.core.MutableState` |
| `opus.core.OpusNode` | `opus.core.UiNode` |
| `OpusTheme`, `OpusColors`, `OpusShapes`, `OpusTypography` | `Theme`, `Colors`, `Shapes`, `Typography` |
| `LocalOpusTheme` | `LocalTheme` |
| `me.methodial.opus.minecraft.OpusScreen` | `opus.minecraft.UiScreen` |
| `me.methodial.opus.minecraft.OpusRenderer` | `opus.minecraft.UiRenderer` |
| `OpusSystemFonts`, `OpusFontRegistry` | `opus.minecraft.Fonts` |
| `OpusShaders.register(event)` | `opus.neoforge.UiClient.initialize(modBus)` during client setup |
| Java `ComponentsKt`, `LayoutKt`, `ModifierKt`, `OpusStateKt`, `ComposerKt` | `Components`, `Layouts`, `Modifiers`, `States`, `Compositions` |
| Java `Modifier.Companion` | `Modifier.EMPTY` |
| `Shadow.radius` | `Shadow.blurRadius` |

Kotlin component calls (`Column`, `Text`, `Button`, `mutableStateOf`) stay the same. Kotlin now has a public top-level `remember { ... }`; callers no longer need composer internals. Java can use `Ui` and `UiScreen.create(Runnable)` / `UiScreen.open(Runnable)` to avoid Kotlin callback types.

`Fonts.system()`, `Fonts.files()` and `Fonts.available()` return `FontOption(id, label, source)`. Discovery keeps the actual font path, including nested directories and uppercase file extensions. `Fonts.register(name, path)` is the portable choice for application-owned fonts. Refresh discovery with `Fonts.refresh()`.

`ShapeRenderer`, `SdfFont`, `FontFace` and `FontDiscovery` are implementation details. Use modifiers and `Fonts` rather than calling the renderer's low-level helpers.

The NeoForge integration moved into `opus-neoforge`. Include that module alongside the Minecraft adapter, initialize it on the client only, and package the shader resources. The showcase build and client constructor provide working source examples.

## Transition lifecycle

`Modifier.offset` now moves descendants as well as the container, and input uses the same coordinates. `Modifier.opacity` also applies to descendants; fully transparent subtrees do not receive input.

Remembered slots are released when their content leaves a successful composition. Keep state above `AnimatedContent` / `AnimatedVisibility` when it must survive navigation or hiding. Outgoing content remains mounted during its exit, with input disabled, and is removed when the exit finishes.
