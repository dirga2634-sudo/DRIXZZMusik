package com.gomouse.pro.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.gomouse.pro.model.InputMapping;

import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Snapshot-based undo/redo for the editor's mapping list. Each call to
 * {@link #push} stores a deep copy (via JSON round-trip, which is cheap for
 * the small lists a profile realistically has) of the mapping list *before*
 * a change is applied, so {@link #undo} can restore it. Redo is cleared on
 * any new edit, matching standard editor undo/redo semantics.
 */
public class UndoRedoManager {

    private static final int MAX_HISTORY = 50;

    private final Gson gson = new Gson();
    private final Type listType = new TypeToken<List<InputMapping>>() {}.getType();

    private final Deque<String> undoStack = new ArrayDeque<>();
    private final Deque<String> redoStack = new ArrayDeque<>();

    /** Call BEFORE mutating the list, passing its current (pre-change) state. */
    public void push(List<InputMapping> currentState) {
        undoStack.push(gson.toJson(currentState));
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Restores the previous state. Pass the list's *current* state so it can
     * be pushed onto the redo stack first.
     */
    public List<InputMapping> undo(List<InputMapping> currentState) {
        if (undoStack.isEmpty()) {
            return null;
        }
        redoStack.push(gson.toJson(currentState));
        String snapshot = undoStack.pop();
        return gson.fromJson(snapshot, listType);
    }

    public List<InputMapping> redo(List<InputMapping> currentState) {
        if (redoStack.isEmpty()) {
            return null;
        }
        undoStack.push(gson.toJson(currentState));
        String snapshot = redoStack.pop();
        return gson.fromJson(snapshot, listType);
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public static List<InputMapping> deepCopyList(Gson gson, List<InputMapping> source) {
        List<InputMapping> copy = new ArrayList<>();
        for (InputMapping m : source) {
            copy.add(gson.fromJson(gson.toJson(m), InputMapping.class));
        }
        return copy;
    }
}
