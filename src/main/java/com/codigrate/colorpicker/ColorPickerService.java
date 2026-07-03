package com.codigrate.colorpicker;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Application-level store for the picker: the last picked color and a short
 * history of recent picks, persisted across IDE restarts.
 */
@Service
@State(name = "CodigrateColorPicker", storages = @Storage("codigrateColorPicker.xml"))
public final class ColorPickerService implements PersistentStateComponent<ColorPickerService.State> {

    public static final int MAX_HISTORY = 12;

    public static class State {
        public List<String> history = new ArrayList<>();
        public String last;
        public List<String> collapsedSections = new ArrayList<>();
    }

    private State state = new State();

    public static ColorPickerService getInstance() {
        return ApplicationManager.getApplication().getService(ColorPickerService.class);
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        this.state = state;
    }

    public @Nullable String getLast() {
        return state.last;
    }

    public @NotNull List<String> getHistory() {
        return new ArrayList<>(state.history);
    }

    public boolean isCollapsed(@NotNull String section) {
        return state.collapsedSections.contains(section);
    }

    public void setCollapsed(@NotNull String section, boolean collapsed) {
        if (collapsed) {
            if (!state.collapsedSections.contains(section)) {
                state.collapsedSections.add(section);
            }
        } else {
            state.collapsedSections.remove(section);
        }
    }

    /** Records a pick: moves the hex to the front of history and remembers it as last. */
    public void addPick(@NotNull String hex) {
        state.last = hex;
        state.history.remove(hex);
        state.history.add(0, hex);
        while (state.history.size() > MAX_HISTORY) {
            state.history.remove(state.history.size() - 1);
        }
    }
}
