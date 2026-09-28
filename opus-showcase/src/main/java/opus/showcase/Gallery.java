package opus.showcase;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import opus.minecraft.Fonts;
import net.minecraft.client.Minecraft;
import opus.core.Components;
import opus.core.CutoutKind;
import opus.core.Layouts;
import opus.core.Modifier;
import opus.core.Modifiers;
import opus.core.MutableState;
import opus.core.States;
import opus.core.TextRole;
import opus.core.Ui;
import opus.core.Easing;
import opus.core.ViewTransition;
import opus.core.SwipeDirection;
import opus.core.InputOptions;
import opus.core.NumberInput;
import opus.core.NumberOptions;
import opus.core.DateInput;

import java.util.List;
import java.util.Set;

/** A compact, interactive gallery of the Opus component library. */
public final class Gallery {
    static final int BACKGROUND = 0xFF0D131C;
    private static final int SURFACE = 0xFF151E2A;
    private static final int SURFACE_RAISED = 0xFF1D2A39;
    private static final int BORDER = 0xFF2A3849;
    private static final int FOREGROUND = 0xFFEAF0F7;
    private static final int MUTED = 0xFF98A8BB;

    private enum Tab {
        CONTROLS("Controls"), TYPOGRAPHY("Typography"), STYLING("Styling"), LAYOUT("Layout");
        final String label;
        Tab(String label) { this.label = label; }
    }

    private record Theme(String name, int primary, int accent, int tint) {}
    private static final List<Theme> THEMES = List.of(
        new Theme("Teal", 0xFF286F66, 0xFF5DD2BD, 0xFF142B2B),
        new Theme("Blue", 0xFF345F96, 0xFF78B5F5, 0xFF17263C),
        new Theme("Plum", 0xFF704A91, 0xFFC59AE8, 0xFF2B1F39),
        new Theme("Amber", 0xFF89602A, 0xFFE9BE70, 0xFF30281A)
    );
    private static final List<String> FRUITS = List.of("Apple", "Berry", "Citrus", "Pear");

    private static final MutableState<Tab> ACTIVE_TAB = States.mutableStateOf(Tab.CONTROLS);
    private static final MutableState<Integer> ACTIVE_THEME = States.mutableStateOf(0);
    private static final MutableState<Boolean> SNAP_ENABLED = States.mutableStateOf(true);
    private static final MutableState<Boolean> PREVIEW_ENABLED = States.mutableStateOf(true);
    private static final MutableState<Integer> APPLY_COUNT = States.mutableStateOf(0);
    private static final MutableState<Float> INTENSITY = States.mutableStateOf(0.65f);
    private static final MutableState<Integer> SELECTED_MODE = States.mutableStateOf(1);
    private static final MutableState<Boolean> DROPDOWN_EXPANDED = States.mutableStateOf(false);
    private static final MutableState<Integer> SELECTED_FRUIT = States.mutableStateOf(0);
    private static final MutableState<Set<Integer>> SELECTED_FRUITS = States.mutableStateOf(Set.of(0, 2));
    private static final MutableState<Boolean> DETAILS_EXPANDED = States.mutableStateOf(false);
    private static final MutableState<Boolean> POPUP_VISIBLE = States.mutableStateOf(false);
    private static final MutableState<String> SELECTED_FONT = States.mutableStateOf("minecraft:default");
    private static final MutableState<String> SELECTED_FONT_LABEL = States.mutableStateOf("Default");
    private static final MutableState<Float> CORNER_RADIUS = States.mutableStateOf(8f);
    private static final MutableState<Integer> CURRENT_PAGE = States.mutableStateOf(0);
    private static final MutableState<Boolean> MOTION_EXPANDED = States.mutableStateOf(false);
    private static final MutableState<Boolean> REDUCED_MOTION = States.mutableStateOf(false);
    private static final MutableState<Integer> TRANSITION_PAGE = States.mutableStateOf(0);
    private static final MutableState<Boolean> TRANSITION_VISIBLE = States.mutableStateOf(true);
    private static final MutableState<Boolean> STYLE_EXPANDED = States.mutableStateOf(false);
    private static final MutableState<String> DISPLAY_NAME = States.mutableStateOf("");
    private static final MutableState<String> AMOUNT = States.mutableStateOf("25");
    private static final MutableState<String> NOTES = States.mutableStateOf("");
    private static final MutableState<String> START_DATE = States.mutableStateOf("");
    private static final MutableState<Boolean> INPUT_SUBMITTED = States.mutableStateOf(false);
    private static final MutableState<String> INPUT_STATUS = States.mutableStateOf("Changes stay in this preview.");

    private Gallery() {}

    public static Function0<Unit> content() { return block(Gallery::renderGallery); }

    private static void renderGallery() {
        Layouts.Column(Modifiers.padding(fullWidth(), 16f), block(() -> {
            header();
            gap(12f);
            Layouts.Grid(4, fullWidth(), 6f, block(() -> {
                for (Tab tab : Tab.values()) {
                    button(tab.label, () -> selectTab(tab), true,
                        ACTIVE_TAB.getValue() == tab ? theme().primary() : SURFACE_RAISED);
                }
            }));
            gap(6f);
            text("Pull sideways fully, then release to switch tabs", TextRole.LABEL, MUTED);
            gap(12f);
            Ui.animatedContent(ACTIVE_TAB.getValue(), tab -> {
                switch (tab) {
                    case CONTROLS -> controls();
                    case TYPOGRAPHY -> typography();
                    case STYLING -> styling();
                    case LAYOUT -> layout();
                }
            }, fullWidth(), ViewTransition.SLIDE_HORIZONTAL, 220);
            gap(14f);
            text("Scroll to explore  /  Esc to close", TextRole.LABEL, MUTED);
            gap(6f);
        }));
    }

    private static void header() {
        Layouts.Row(fullWidth(), block(() -> {
            Layouts.Column(Modifiers.weight(Modifier.Companion, 1f), block(() -> {
                text("OPUS", TextRole.HEADING_2, FOREGROUND);
                gap(3f);
                text("Component gallery", TextRole.LABEL, MUTED);
            }));
            Layouts.Column(Modifier.Companion, block(() -> {
                gap(3f);
                text("LIVE PREVIEW", TextRole.LABEL, theme().accent());
                gap(5f);
                text("NeoForge 1.21.1", TextRole.LABEL, MUTED);
            }));
        }));
    }

    private static void controls() {
        inputs();
        gap(12f);
        Layouts.Grid(2, fullWidth(), 10f, block(() -> {
            panel("Preferences", "Try a few settings.", () -> {
                Components.Checkbox(SNAP_ENABLED.getValue(), value -> {
                    SNAP_ENABLED.setValue(value); return Unit.INSTANCE;
                }, Modifier.Companion, "Snap to grid", theme().accent());
                gap(10f);
                Layouts.Row(Modifier.Companion, block(() -> {
                    Components.Switch(PREVIEW_ENABLED.getValue(), value -> {
                        PREVIEW_ENABLED.setValue(value); return Unit.INSTANCE;
                    }, Modifier.Companion, theme().accent());
                    horizontalGap(8f);
                    text("Live preview", TextRole.LABEL, FOREGROUND);
                }));
                gap(14f);
                Layouts.Grid(2, fullWidth(), 6f, block(() -> {
                    button("Apply", () -> APPLY_COUNT.setValue(APPLY_COUNT.getValue() + 1));
                    button("Disabled", () -> {}, false, theme().primary());
                }));
                gap(10f);
                text(APPLY_COUNT.getValue() == 0 ? "Ready to apply" : "Applied " + APPLY_COUNT.getValue() + " times",
                    TextRole.LABEL, MUTED);
            });
            panel("Intensity", Math.round(INTENSITY.getValue() * 100) + "% of the range", () -> {
                Components.Slider(INTENSITY.getValue(), value -> {
                    INTENSITY.setValue(value); return Unit.INSTANCE;
                }, fullWidth(), 0f, 1f, theme().accent());
                gap(8f);
                Components.ProgressBar(Ui.animateFloat(INTENSITY.getValue()), fullWidth(), theme().accent());
                gap(12f);
                Components.RadioGroup(List.of("Quiet", "Balanced", "Bold"), SELECTED_MODE.getValue(), value -> {
                    SELECTED_MODE.setValue(value); return Unit.INSTANCE;
                }, fullWidth(), theme().accent());
            });
        }));
        gap(12f);
        Layouts.Grid(2, fullWidth(), 10f, block(() -> {
            panel("Single choice", "Open the menu.", () -> {
                Components.Dropdown(FRUITS, SELECTED_FRUIT.getValue(), value -> {
                    SELECTED_FRUIT.setValue(value); return Unit.INSTANCE;
                }, DROPDOWN_EXPANDED.getValue(), value -> {
                    DROPDOWN_EXPANDED.setValue(value); return Unit.INSTANCE;
                }, fullWidth());
                gap(10f);
                text("Selected: " + FRUITS.get(SELECTED_FRUIT.getValue()), TextRole.LABEL, MUTED);
            });
            panel("Multiple choice", "Pick your favorites.", () -> Components.MultiSelect(
                FRUITS, SELECTED_FRUITS.getValue(), value -> {
                    SELECTED_FRUITS.setValue(value); return Unit.INSTANCE;
                }, fullWidth(), theme().accent()));
        }));
        gap(12f);
        panel("Disclosure", "Reveal a little more.", () -> {
            Components.Accordion("Show details", DETAILS_EXPANDED.getValue(), value -> {
                DETAILS_EXPANDED.setValue(value); return Unit.INSTANCE;
            }, fullWidth(), block(() -> {
                gap(5f);
                text("Your settings survive every update.", TextRole.LABEL, MUTED);
                gap(8f);
            }));
            gap(10f);
            Layouts.Grid(2, fullWidth(), 8f, block(() -> {
                button(POPUP_VISIBLE.getValue() ? "Hide popup" : "Show popup",
                    () -> POPUP_VISIBLE.setValue(!POPUP_VISIBLE.getValue()));
                Components.Tooltip("A hint, right where you need it.", Modifier.Companion,
                    block(() -> button("Hover for a hint", () -> {}, true, SURFACE_RAISED)));
            }));
            if (POPUP_VISIBLE.getValue()) {
                gap(10f);
                Components.Popup(() -> {
                    POPUP_VISIBLE.setValue(false); return Unit.INSTANCE;
                }, insetSurface(theme().tint()), block(() -> {
                    text("A little extra context", TextRole.PARAGRAPH, FOREGROUND);
                    gap(6f);
                    text("This content is controlled by live state.", TextRole.LABEL, MUTED);
                    gap(10f);
                    button("Dismiss", () -> POPUP_VISIBLE.setValue(false));
                }));
            }
        });
    }

    private static void inputs() {
        panel("Text inputs", "Tab between fields. Enter to apply.", () -> {
            Layouts.Grid(2, fullWidth(), 10f, block(() -> {
                Ui.textField(DISPLAY_NAME.getValue(), DISPLAY_NAME::setValue, "Display name", fullWidth(),
                    InputOptions.DEFAULT.withPlaceholder("Your name").withMaxLength(40).withAccent(theme().accent())
                        .withHelperText("Up to 40 characters.")
                        .withError(INPUT_SUBMITTED.getValue() && DISPLAY_NAME.getValue().isBlank() ? "Enter a name to continue." : null),
                    Gallery::applyInputs);
                Double amount = NumberInput.parseOrNull(AMOUNT.getValue());
                Ui.numberField(AMOUNT.getValue(), AMOUNT::setValue, "Amount", fullWidth(),
                    InputOptions.DEFAULT.withPlaceholder("0 to 100").withMaxLength(12).withAccent(theme().accent())
                        .withHelperText("Use arrows or type 0 to 100.")
                        .withError(amount == null || amount < 0 || amount > 100 ? "Enter a number from 0 to 100." : null),
                    Gallery::applyInputs, new NumberOptions(1.0, 0.0, 100.0));
            }));
            gap(12f);
            Ui.dateField(START_DATE.getValue(), START_DATE::setValue, "Start date", fullWidth(),
                InputOptions.DEFAULT.withPlaceholder("yyyy-MM-dd").withHelperText("Type a date or choose one from the calendar.")
                    .withAccent(theme().accent()).withError(!START_DATE.getValue().isEmpty() && DateInput.parseOrNull(START_DATE.getValue()) == null
                        ? "Enter a valid date as yyyy-MM-dd." : null));
            gap(12f);
            Ui.textArea(NOTES.getValue(), NOTES::setValue, "Notes", fullWidth(),
                InputOptions.DEFAULT.withPlaceholder("Write a few lines...").withMaxLength(240).withAccent(theme().accent())
                    .withHelperText(NOTES.getValue().length() + " / 240 characters"), 4);
            gap(12f);
            Layouts.Grid(2, fullWidth(), 10f, block(() -> {
                Ui.textField("Preview workspace", ignored -> {}, "Read only", fullWidth(),
                    InputOptions.DEFAULT.withReadOnly(true).withHelperText("Select and copy this value."));
                Ui.textField("Unavailable", ignored -> {}, "Disabled", fullWidth(),
                    InputOptions.DEFAULT.withEnabled(false).withHelperText("Skipped by keyboard focus."));
            }));
            gap(12f);
            Layouts.Grid(2, fullWidth(), 8f, block(() -> {
                button("Apply inputs", Gallery::applyInputs);
                button("Clear", () -> {
                    DISPLAY_NAME.setValue(""); NOTES.setValue(""); AMOUNT.setValue("25"); START_DATE.setValue("");
                    INPUT_SUBMITTED.setValue(false); INPUT_STATUS.setValue("Changes stay in this preview.");
                }, true, SURFACE_RAISED);
            }));
            gap(8f);
            text(INPUT_STATUS.getValue(), TextRole.LABEL, MUTED);
        });
    }

    private static void applyInputs() {
        INPUT_SUBMITTED.setValue(true);
        Double amount = NumberInput.parseOrNull(AMOUNT.getValue());
        INPUT_STATUS.setValue(DISPLAY_NAME.getValue().isBlank() || amount == null || amount < 0 || amount > 100
            || !START_DATE.getValue().isEmpty() && DateInput.parseOrNull(START_DATE.getValue()) == null
            ? "Check the highlighted fields." : "Inputs applied to this preview.");
    }

    private static void typography() {
        panel("Type specimen", "A clear visual rhythm.", () -> {
            text("Make room for ideas.", TextRole.HEADING_2, FOREGROUND);
            gap(9f);
            text("A clear hierarchy", TextRole.HEADING_3, FOREGROUND);
            gap(7f);
            text("Readable text for everyday interfaces.", TextRole.PARAGRAPH, FOREGROUND);
            gap(7f);
            text("Small details make the difference.", TextRole.LABEL, MUTED);
        });
        gap(12f);
        panel("Font playground", "Selected: " + SELECTED_FONT_LABEL.getValue(), () -> {
            Layouts.Box(insetSurface(theme().tint()), block(() -> {
                Components.Text("The quick brown fox", TextRole.HEADING_1, Modifier.Companion,
                    SELECTED_FONT.getValue(), FOREGROUND);
                gap(8f);
                Components.Text("AVATAR  To Wa  0123456789", TextRole.PARAGRAPH, Modifier.Companion,
                    SELECTED_FONT.getValue(), theme().accent());
                gap(8f);
                Components.Text("Small type, clear details.  Il1 O0", TextRole.LABEL, Modifier.Companion,
                    SELECTED_FONT.getValue(), FOREGROUND);
                gap(8f);
                Components.Text("Café · naïve · Ångström", TextRole.PARAGRAPH, Modifier.Companion,
                    SELECTED_FONT.getValue(), FOREGROUND);
                gap(8f);
                Components.Text("Привіт, Україно! · Αθήνα", TextRole.PARAGRAPH, Modifier.Companion,
                    SELECTED_FONT.getValue(), FOREGROUND);
            }));
            gap(12f);
            Layouts.Grid(3, fullWidth(), 6f, block(() -> {
                fontButton("Default", "minecraft:default");
                fontButton("Uniform", "minecraft:uniform");
                fontButton("Enchantment", "minecraft:alt");
            }));
            gap(14f);
            text("FROM YOUR COMPUTER", TextRole.LABEL, MUTED);
            gap(8f);
            Layouts.Grid(2, fullWidth(), 6f, block(() -> {
                int count = 0;
                for (Fonts.FontOption font : Fonts.system()) {
                    if (count++ == 8) break;
                    fontButton(font.label(), font.id());
                }
            }));
        });
    }

    private static void styling() {
        transitions();
        gap(12f);
        motion();
        gap(12f);
        panel("Color theme", "A single accent across the gallery.", () -> {
            Layouts.Grid(4, fullWidth(), 6f, block(() -> {
                for (int index = 0; index < THEMES.size(); index++) {
                    final int selected = index;
                    Theme option = THEMES.get(index);
                    button(option.name(), () -> ACTIVE_THEME.setValue(selected), true, option.primary());
                }
            }));
            gap(12f);
            Layouts.Box(insetSurface(theme().tint()), block(() -> {
                text(theme().name() + " is active", TextRole.PARAGRAPH, theme().accent());
                gap(6f);
                text("Buttons, inputs and highlights update together.", TextRole.LABEL, MUTED);
                gap(12f);
                Components.ProgressBar(0.7f, fullWidth(), theme().accent());
            }));
        });
        gap(12f);
        panel("Shape studio", "Corner radius: " + Math.round(CORNER_RADIUS.getValue()) + " px", () -> {
            Components.Slider(CORNER_RADIUS.getValue(), value -> {
                CORNER_RADIUS.setValue(value); return Unit.INSTANCE;
            }, fullWidth(), 0f, 20f, theme().accent());
            gap(14f);
            float radius = CORNER_RADIUS.getValue();
            Layouts.Grid(2, fullWidth(), 12f, block(() -> {
                Modifier rounded = Modifiers.padding(Modifiers.border(
                    Modifiers.background(Modifiers.height(Modifier.Companion, 52f), theme().tint(), radius),
                    1f, theme().accent(), radius), 12f);
                specimen("Border", "Clean edges", rounded);
                Modifier shadow = Modifiers.shadow(Modifiers.padding(Modifiers.background(
                    Modifiers.height(Modifier.Companion, 52f), SURFACE_RAISED, radius), 12f),
                    6f, 0f, 3f, 0x70000000);
                specimen("Shadow", "A little depth", shadow);
                Modifier cutout = Modifiers.paddingEach(Modifiers.cutout(Modifiers.background(
                    Modifiers.height(Modifier.Companion, 62f), SURFACE_RAISED, radius),
                    CutoutKind.NOTCH_TOP, 16f), 12f, 22f, 12f, 8f);
                specimen("Cutout", "A shaped surface", cutout);
                Modifier outline = Modifiers.outline(Modifiers.padding(Modifiers.background(
                    Modifiers.height(Modifier.Companion, 62f), SURFACE_RAISED, radius), 12f),
                    1f, theme().accent(), 3f);
                specimen("Outline", "An offset stroke", outline);
            }));
        });
    }

    private static void transitions() {
        panel("Transitions", "Views, visibility and everyday style changes.", () -> {
            Layouts.Grid(2, fullWidth(), 6f, block(() -> {
                button("Overview", () -> TRANSITION_PAGE.setValue(0));
                button("Details", () -> TRANSITION_PAGE.setValue(1));
            }));
            gap(10f);
            Ui.animatedContent(TRANSITION_PAGE.getValue(), page -> {
                Layouts.Box(insetSurface(page == 0 ? theme().tint() : SURFACE_RAISED), block(() -> {
                    text(page == 0 ? "Everything in one place" : "A closer look", TextRole.PARAGRAPH, FOREGROUND);
                    gap(6f);
                    text(page == 0 ? "Switch views to try the transition." : "Fade out, swap views, slide in.", TextRole.LABEL, MUTED);
                }));
            }, Modifiers.height(fullWidth(), 62f), ViewTransition.SLIDE_HORIZONTAL, 420);
            gap(10f);
            button(TRANSITION_VISIBLE.getValue() ? "Hide message" : "Show message",
                () -> TRANSITION_VISIBLE.setValue(!TRANSITION_VISIBLE.getValue()));
            Ui.animatedVisibility(TRANSITION_VISIBLE.getValue(), () -> {
                gap(10f);
                Layouts.Box(insetSurface(theme().tint()), block(() ->
                    text("Content stays until its exit finishes.", TextRole.LABEL, FOREGROUND)));
            }, fullWidth());
            gap(14f);
            boolean expanded = STYLE_EXPANDED.getValue();
            Modifier style = Modifiers.size(Modifier.EMPTY, expanded ? 180f : 100f, expanded ? 60f : 40f);
            style = Modifiers.padding(Modifiers.background(style,
                expanded ? 0xFF704A91 : theme().primary(), expanded ? 18f : 5f), expanded ? 14f : 8f);
            Layouts.Box(Ui.transition(style, 400, Easing.EASE_IN_OUT), block(() ->
                text("Restyle me", TextRole.LABEL, FOREGROUND)));
            gap(10f);
            button("Change style", () -> STYLE_EXPANDED.setValue(!STYLE_EXPANDED.getValue()));
        });
    }

    private static void motion() {
        panel("Motion studio", "Move, reshape and blend colors together.", () -> {
            boolean expanded = MOTION_EXPANDED.getValue();
            float position = Ui.animateFloat(expanded ? 100f : 0f, 650, Easing.EASE_IN_OUT);
            float size = Ui.animateFloat(expanded ? 48f : 32f, 650, Easing.EASE_IN_OUT);
            float radius = Ui.animateFloat(expanded ? 24f : 6f, 650, Easing.EASE_IN_OUT);
            int color = Ui.animateColor(expanded ? 0xFFC59AE8 : theme().accent(), 650, Easing.EASE_IN_OUT);
            float progress = Ui.animateFloat(expanded ? 1f : 0f, 650, Easing.EASE_IN_OUT);
            Layouts.Box(Modifiers.padding(Modifiers.height(
                Modifiers.background(fullWidth(), BACKGROUND, 6f), 76f), 12f), block(() ->
                Layouts.Box(Modifiers.background(Modifiers.offset(
                    Modifiers.size(Modifier.EMPTY, size, size), position, 0f), color, radius), block(() -> {}))));
            gap(10f);
            Components.ProgressBar(progress, fullWidth(), color);
            gap(12f);
            button(expanded ? "Reverse animation" : "Play animation",
                () -> MOTION_EXPANDED.setValue(!MOTION_EXPANDED.getValue()));
            gap(12f);
            Components.Checkbox(REDUCED_MOTION.getValue(), value -> {
                if (Minecraft.getInstance().screen instanceof GalleryScreen screen) screen.setReducedMotion(value);
                REDUCED_MOTION.setValue(value);
                return Unit.INSTANCE;
            }, Modifier.EMPTY, "Reduce motion", theme().accent());
        });
    }

    static boolean reducedMotion() { return REDUCED_MOTION.getValue(); }

    private static void layout() {
        panel("A flexible grid", "Three columns with a shared gutter.", () -> {
            Layouts.Grid(3, fullWidth(), 8f, block(() -> {
                for (int index = 1; index <= 6; index++) {
                    final int cell = index;
                    Layouts.Box(insetSurface(cell == 1 ? theme().tint() : SURFACE_RAISED), block(() -> {
                        text("0" + cell, TextRole.HEADING_3, cell == 1 ? theme().accent() : FOREGROUND);
                        gap(5f);
                        text("Grid cell", TextRole.LABEL, MUTED);
                    }));
                }
            }));
        });
        gap(12f);
        panel("Minecraft content", "Textures and items, in the same layout.", () -> {
            Layouts.Row(Modifier.Companion, block(() -> {
                Components.Image("minecraft:textures/block/stone.png", Modifiers.size(Modifier.Companion, 40f, 40f), "Stone texture");
                horizontalGap(12f);
                Layouts.Column(Modifier.Companion, block(() -> {
                    text("Inventory preview", TextRole.LABEL, MUTED);
                    gap(8f);
                    Layouts.Row(Modifier.Companion, block(() -> {
                        Components.ItemSlot("minecraft:diamond_sword", 1, Modifier.Companion, null);
                        horizontalGap(6f);
                        Components.ItemSlot("minecraft:apple", 5, Modifier.Companion, null);
                        horizontalGap(6f);
                        Components.ItemSlot("minecraft:ender_pearl", 16, Modifier.Companion, null);
                    }));
                }));
            }));
        });
        gap(12f);
        panel("Browse a collection", "Six rows per page. Forty-eight in total.", () -> {
            Components.ListColumn(6, fullWidth(), row -> {
                int item = CURRENT_PAGE.getValue() * 6 + row + 1;
                Layouts.Row(Modifiers.padding(Modifiers.background(fullWidth(),
                    row % 2 == 0 ? SURFACE_RAISED : SURFACE, 4f), 8f), block(() -> {
                    Components.Text(String.format("Component %02d", item), TextRole.LABEL,
                        Modifiers.weight(Modifier.Companion, 1f), null, FOREGROUND);
                    text("Ready", TextRole.LABEL, theme().accent());
                }));
                if (row < 5) gap(4f);
                return Unit.INSTANCE;
            });
            gap(12f);
            Components.Pagination(CURRENT_PAGE.getValue(), 8, value -> {
                CURRENT_PAGE.setValue(value); return Unit.INSTANCE;
            }, fullWidth());
        });
    }

    private static void panel(String title, String subtitle, Runnable content) {
        Modifier style = Modifiers.padding(Modifiers.border(
            Modifiers.background(fullWidth(), SURFACE, 8f), 1f, BORDER, 8f), 12f);
        Layouts.Box(style, block(() -> {
            text(title, TextRole.PARAGRAPH, FOREGROUND);
            gap(5f);
            text(subtitle, TextRole.LABEL, MUTED);
            gap(12f);
            content.run();
        }));
    }

    private static void specimen(String title, String subtitle, Modifier modifier) {
        Layouts.Box(modifier, block(() -> {
            text(title, TextRole.LABEL, FOREGROUND);
            gap(5f);
            text(subtitle, TextRole.LABEL, MUTED);
        }));
    }

    private static void fontButton(String label, String fontId) {
        button(label, () -> {
            SELECTED_FONT_LABEL.setValue(label);
            SELECTED_FONT.setValue(fontId);
        }, true, SELECTED_FONT.getValue().equals(fontId) ? theme().primary() : SURFACE_RAISED);
    }

    private static void button(String label, Runnable action) { button(label, action, true, theme().primary()); }
    private static void button(String label, Runnable action, boolean enabled, int color) {
        Components.Button(label, block(action), Modifier.Companion, enabled, color);
    }
    private static void selectTab(Tab tab) {
        ACTIVE_TAB.setValue(tab);
        if (Minecraft.getInstance().screen instanceof GalleryScreen gallery) gallery.scrollToTop();
    }

    static boolean canSwipeTab(SwipeDirection direction) {
        int next = ACTIVE_TAB.getValue().ordinal() + (direction == SwipeDirection.LEFT ? 1 : -1);
        return next >= 0 && next < Tab.values().length;
    }

    static void swipeTab(SwipeDirection direction) {
        Tab[] tabs = Tab.values();
        int next = ACTIVE_TAB.getValue().ordinal() + (direction == SwipeDirection.LEFT ? 1 : -1);
        if (next >= 0 && next < tabs.length) selectTab(tabs[next]);
    }
    private static Theme theme() { return THEMES.get(ACTIVE_THEME.getValue()); }
    private static Modifier fullWidth() { return Modifiers.fillMaxWidth(Modifier.Companion, 1f); }
    private static Modifier insetSurface(int color) {
        return Modifiers.padding(Modifiers.background(fullWidth(), color, 5f), 10f);
    }
    private static void text(String value, TextRole role, int color) {
        Components.Text(value, role, Modifier.Companion, null, color);
    }
    private static void gap(float height) { Layouts.Spacer(Modifiers.height(Modifier.Companion, height)); }
    private static void horizontalGap(float width) { Layouts.Spacer(Modifiers.width(Modifier.Companion, width)); }
    private static Function0<Unit> block(Runnable action) {
        return () -> { action.run(); return Unit.INSTANCE; };
    }
}
